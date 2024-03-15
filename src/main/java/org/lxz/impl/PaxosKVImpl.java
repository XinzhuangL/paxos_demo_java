package org.lxz.impl;

import com.google.common.collect.Maps;
import io.grpc.stub.StreamObserver;
import org.lxz.common.Tools;
import org.lxz.paxoskv.PaxosKVGrpc;
import org.lxz.paxoskv.Paxoskv;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

// Prepare handles Prepare request.
// Handling Prepare needs only the `Bal` field.
// The reply contains all fields of an Acceptor thus it just replies the
// Acceptor itself as reply data structure.
public class PaxosKVImpl extends PaxosKVGrpc.PaxosKVImplBase {

    ReentrantLock lock;
    Map<String, Versions> storage;

    public Map<String, Versions> getStorage() {
        return storage;
    }

    public PaxosKVImpl() {
        super();
        this.lock = new ReentrantLock();
        this.storage = Maps.newHashMap();
    }

    @Override
    public void prepare(Paxoskv.Proposer request, StreamObserver<Paxoskv.Acceptor> responseObserver) {
        Tools.log("Acceptor: recv Prepare-request: %s", request);
        Version v = getLockedVersion(request.getId());
        Paxoskv.Acceptor replay;
        try {
            replay = v.acceptor;
            if(Tools.GE(request.getBal(), replay.getLastBal())) {
                replay = Paxoskv.Acceptor.newBuilder()
                        .setVal(replay.getVal())
                        .setVBal(replay.getVBal())
                        .setLastBal(request.getBal())
                        .build();
            }

        } finally {
            v.lock.unlock();
        }
        responseObserver.onNext(replay);
        responseObserver.onCompleted();
    }

    @Override
    public void accept(Paxoskv.Proposer request, StreamObserver<Paxoskv.Acceptor> responseObserver) {
        Tools.log("Acceptor: recv Accept-request:\n %s", request);
        Version v = getLockedVersion(request.getId());
        Paxoskv.Acceptor reply;
        try {

            // a := &X{}
            // `b := &*a` does not deref the reference, b and a are the same pointer.
            Paxoskv.BallotNum d = v.acceptor.getLastBal();
            reply = Paxoskv.Acceptor.newBuilder()
                    .setLastBal(d)
                    .build();

            // article say acceptor's LastBal equal proposer's Bal will accept it
            // but if greater, point that a large proposer's Bal has been through phrase1 with most acceptor,
            // the same accept is
            if (Tools.GE(request.getBal(), v.acceptor.getLastBal())) {
                v.acceptor = Paxoskv.Acceptor.newBuilder()
                        .setLastBal(request.getBal())
                        .setVal(request.getVal())
                        .setVBal(request.getBal())
                        .build();
            }
        } finally {
            v.lock.unlock();
        }
        responseObserver.onNext(reply);
        responseObserver.onCompleted();

    }

    Version getLockedVersion(Paxoskv.PaxosInstanceId id) {
        this.lock.lock();
        Version v;
        try {
            String key = id.getKey();
            long ver = id.getVer();
            if(!this.storage.containsKey(key)) {
                this.storage.put(key, new Versions());
            }
            Versions versions = this.storage.get(key);

            if(!versions.map.containsKey(ver)) {
                // initialize an empty paxos instance
                versions.map.put(ver, new Version(Paxoskv.Acceptor.newBuilder().build()));
            }
            v = versions.map.get(ver);
            Tools.log("Acceptor: getLockedVersion: %s", v);
            v.lock.lock();

        } finally {
            this.lock.unlock();
        }
        return v;
    }

}
