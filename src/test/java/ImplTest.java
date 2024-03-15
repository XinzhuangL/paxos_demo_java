import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import org.junit.Test;
import org.lxz.impl.PaxosKVImpl;
import org.lxz.impl.Version;
import org.lxz.paxoskv.Paxoskv;

public class ImplTest {

    @Test
    public void test1() {
        System.out.println("test1");
        PaxosKVImpl kvServer = new PaxosKVImpl();
        Paxoskv.Proposer p = Paxoskv.Proposer.newBuilder()
                .setId(
                        Paxoskv.PaxosInstanceId.newBuilder().setKey("x").setVer(0).build()
                )
                .setBal(
                        Paxoskv.BallotNum.newBuilder().setN(-1).build()
                )
                .build();

        kvServer.accept(p, new StreamObserver<Paxoskv.Acceptor>() {
            @Override
            public void onNext(Paxoskv.Acceptor value) {

            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onCompleted() {

            }
        });

        Version v = kvServer.getStorage().get("x").map.get(0L);

        System.out.println();

    }
}
