package org.lxz.impl;

import com.google.common.collect.Lists;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import org.lxz.common.NotEnoughQuorumException;
import org.lxz.common.Tools;
import org.lxz.common.Triple;
import org.lxz.paxoskv.PaxosKVGrpc;
import org.lxz.paxoskv.Paxoskv;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Impl {

    private static final Long BASE_PORT = 3333L;


    /**
     *
     * RunPaxos execute the paxos phase-1 and phase-2 to establish a value.
     * `val` is the value caller wants to propose.
     * It returns the established value, which may be a voted value that is not `val`,
     *
     * If `val` is not nil, it writes it into the specified version of a record.
     * The record key and the version is specified by p.PaxosInstanceId, since every
     * update of a record(every version) is impl by a paxos instance.
     *
     * if `val` is nil, it acts as a reading operation:
     * it reads the specified version of a record by running a paxos without propose
     * any value: This func will finish paxos phase-2 to make it safe if a voted
     * value is found, otherwise, it just returns nil without running phase-2.
     */

    public static Paxoskv.Value runPaxos(Paxoskv.Proposer p, List<Long> acceptorIds, Paxoskv.Value val) {
        int quorum = acceptorIds.size() / 2 + 1;
        // for
        while (true) {
            Triple<Paxoskv.Value, Paxoskv.BallotNum, Exception> phase1 =
                    phase1(p, acceptorIds, quorum);
            Paxoskv.Value maxVotedVal = phase1.getFirst();
            Paxoskv.BallotNum higherBal = phase1.getSecond();
            Exception err = phase1.getThird();

            if(err != null) {
                Tools.log("Proposer: fail to run phase-1: highest ballot: %s, increment ballot and retry", higherBal);
                p = Paxoskv.Proposer.newBuilder(p)
                        .setBal(
                                Paxoskv.BallotNum.newBuilder().setN(higherBal.getN() + 1)
                        ).build();
                continue;
            }

            if(maxVotedVal == null) {
                Tools.log("Proposer: no voted value seen, propose my value: %s", val);
            } else {
                val = maxVotedVal;
            }

            if (val == null) {
                Tools.log("Proposer: no value to propose in phase-2, quit");
                return null;
            }

            p.newBuilderForType().setVal(val);
            Tools.log("Proposer: proposer chose value to propose: %s", p.getVal());

            Triple<Paxoskv.BallotNum, Exception, Void> tuple = phase2(p, acceptorIds, quorum);
            higherBal = tuple.getFirst();
            err = tuple.getSecond();
            if(err != null) {
                Tools.log("Proposer: fail to run phase-2: highest ballot: %s, increment ballot and retry", higherBal);
                p.getBal().newBuilderForType().setN(higherBal.getN() + 1);
                continue;
            }
            Tools.log("Proposer: value is voted by a quorum and has been safe: %s", maxVotedVal);
            return p.getVal();
        }
    }

    /**
     * Phase1 run paxos phase-1 on the specified acceptorIds.
     * If a higher ballot number is seen and phase-1 failed to constitute a quorum,
     * one of the higher ballot number and a NotEnoughQuorum is returned.
     */
    public static Triple<Paxoskv.Value, Paxoskv.BallotNum, Exception> phase1(
            Paxoskv.Proposer proposer,
            List<Long> acceptorIds,
            int quorum
    ) {
        List<Paxoskv.Acceptor> replies = rpcToAll(proposer, acceptorIds, "Prepare");
        int ok = 0;
        Paxoskv.BallotNum higherBal = proposer.getBal();
        // maxVoted
        Paxoskv.Acceptor maxVoted = Paxoskv.Acceptor.newBuilder().build();
        // for replies
        for(Paxoskv.Acceptor reply : replies) {
            Tools.log("Proposer: handling Prepare reply: %s", reply);
            if(!Tools.GE(proposer.getBal(), reply.getLastBal())) {
                if (Tools.GE(reply.getLastBal(), higherBal)) {
                    higherBal = reply.getLastBal();
                }
                continue;
            }

            // find the voted value with highest vbal
            // todo may null
            if (Tools.GE(reply.getVBal(), maxVoted.getVBal())) {
                maxVoted = reply;
            }
            ok += 1;
            if (ok == quorum) {
                return new Triple<>(maxVoted.getVal(), null, null);
            }
        }
        return new Triple<>(null, higherBal, NotEnoughQuorumException.INSTANCE);
    }

    /**
     *
     * @param p
     * @param acceptorIds
     * @param quorum
     * @return
     *  Phase2 run paxos phase-2 on the specified acceptorIds.
     *  If a higher ballot number is seen and phase-2 failed to constitute a quorum,
     *  one of the higher ballot number and a NotEnoughQuorum is returned.
     *
     */

    public static Triple<Paxoskv.BallotNum, Exception, Void> phase2(Paxoskv.Proposer p, List<Long> acceptorIds, int quorum) {
        List<Paxoskv.Acceptor> replies = rpcToAll(p, acceptorIds, "Accept");
        int ok = 0;
        Paxoskv.BallotNum higherBal = p.getBal();
        for(Paxoskv.Acceptor reply : replies) {
            Tools.log("Proposer: handling Accept reply: %s", reply);
            if (!Tools.GE(p.getBal(), reply.getLastBal())) {
                if (Tools.GE(reply.getLastBal(), higherBal)) {
                    higherBal = reply.getLastBal();
                }
                continue;
            }
            ok += 1;
            if (ok == quorum) {
                return new Triple<>(null, null, null);
            }

        }
        return new Triple<>(higherBal, NotEnoughQuorumException.INSTANCE,null);
    }

    // rpcToAll send Prepare or Accept RPC to the specified Acceptors.
    public static List<Paxoskv.Acceptor> rpcToAll(Paxoskv.Proposer p, List<Long> acceptorIds, String action) {
        List<Paxoskv.Acceptor> replies = Lists.newArrayList();

        for(Long aid : acceptorIds) {
            String address = String.format("127.0.0.1:%d", BASE_PORT + aid);
            // Set up a connection to the service
            ManagedChannel channel = ManagedChannelBuilder.forTarget(address)
                    .usePlaintext()
                    .build();
            PaxosKVGrpc.PaxosKVBlockingStub paxosKVBlockingStub = PaxosKVGrpc.newBlockingStub(channel);

            Paxoskv.Acceptor reply = null;
            if(action.equals("Prepare")) {
                reply = paxosKVBlockingStub.prepare(p);
            } else if(action.equals("Accept")) {
                reply = paxosKVBlockingStub.accept(p);
            }

            if (reply != null) {
                replies.add(reply);
            }
        }
        return replies;
    }

    // ServeAcceptors starts a grpc server for every acceptor
    public static List<Server> serveAcceptors(List<Long> acceptorIds) throws IOException {
        List<Server> servers = Lists.newArrayList();
        for(Long acceptorId : acceptorIds) {

            int targetPort = (int) (BASE_PORT + acceptorId);
            Server server = Grpc.newServerBuilderForPort(targetPort, InsecureServerCredentials.create())
                    .addService(new PaxosKVImpl())
                    .build()
                    .start();

            Tools.log("Acceptor-%d serving on %s ...", acceptorId, targetPort);
            servers.add(server);
        }
        return servers;

    }







}
