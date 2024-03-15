import com.google.common.collect.Lists;
import io.grpc.Server;
import org.junit.Assert;
import org.junit.Test;
import org.lxz.common.Tools;
import org.lxz.common.Triple;
import org.lxz.impl.Impl;
import org.lxz.impl.PaxosKVImpl;
import org.lxz.paxoskv.Paxoskv;

import java.util.List;


public class TestCase1SingleProposer {

    @Test
    public void test1() {
        // slide-32: 1 Proposer, 3 Acceptor, only two of them are involved.
        // The Proposer finishes a paxos without conflict.

        List<Long> acceptorIds = Lists.newArrayList(0L, 1L, 2L);
        int quorum = 2;
        List<Server> servers = Lists.newArrayList();
        try {
            // create 3 Server to provide service
            servers = Impl.serveAcceptors(acceptorIds);

            // The proposer try to set i0 = 10;
            Paxoskv.PaxosInstanceId paxosInstanceId = Paxoskv.PaxosInstanceId.newBuilder()
                    .setKey("i")
                    .setVer(0)
                    .build();

            // proposer
            Paxoskv.Proposer proposer = Paxoskv.Proposer.newBuilder()
                    .setId(paxosInstanceId)
                    .setBal(Paxoskv.BallotNum.newBuilder()
                            .setN(0)
                            .setProposerId(10)
                            .build())
                    .build();

            // Phase 1 will be done without seeing other ballot, nor other voted value.
            Triple<Paxoskv.Value, Paxoskv.BallotNum, Exception> phase1Res = Impl.phase1(proposer, acceptorIds, quorum);
            assert phase1Res.getFirst() == null; // no voted value
            assert phase1Res.getSecond() == null; // no other proposer is seen
            assert phase1Res.getThird() == null;  // constituted a quorum

            // Thus the Proposer choose a new value to propose
            proposer.newBuilderForType().setVal(Paxoskv.Value.newBuilder().setVi64(10).build());

            // Phase 2 only propose 2 acceptors
            Triple<Paxoskv.BallotNum, Exception, Void> phase2Res =
                    Impl.phase2(proposer, Lists.newArrayList(0L, 1L), quorum);
            assert phase2Res.getFirst() == null;  // no other proposer is seen
            assert phase2Res.getSecond() == null; // constituted a quorum

        } catch (Exception e) {
            Tools.log("catch some exception: %s", e.getMessage());
        } finally {
            for(Server server : servers) {
                server.shutdown();
            }
        }

    }
}
