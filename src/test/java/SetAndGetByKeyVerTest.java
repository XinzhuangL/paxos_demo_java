import com.google.common.collect.Lists;
import io.grpc.Server;
import org.junit.Test;
import org.lxz.common.Tools;
import org.lxz.impl.Impl;
import org.lxz.paxoskv.Paxoskv;

import java.util.List;

public class SetAndGetByKeyVerTest {



    @Test
    public void test1() {

        // In this example it set or get a key_ver by running a paxos instance.

        List<Long> acceptorIds = Lists.newArrayList(0L, 1L, 2L);
        List<Server> servers = Lists.newArrayList();
        try {
            servers = Impl.serveAcceptors(acceptorIds);

            // set foo0 = 5
            Paxoskv.Proposer prop = Paxoskv.Proposer.newBuilder()
                    .setId(Paxoskv.PaxosInstanceId.newBuilder().setKey("foo").setVer(0).build())
                    .setBal(Paxoskv.BallotNum.newBuilder().setN(0).setProposerId(2).build())
                    .build();
            Paxoskv.Value value = Impl.runPaxos(prop, acceptorIds, Paxoskv.Value.newBuilder().setVi64(5).build());
            Tools.log("written: %s", value.getVi64());

            // get foo0
            Paxoskv.Proposer.newBuilder()
                    .setId(Paxoskv.PaxosInstanceId.newBuilder().setKey("foo").setVer(0).build())
                    .setBal(Paxoskv.BallotNum.newBuilder().setN(0).setProposerId(2).build())
                    .build();
            value = Impl.runPaxos(prop, acceptorIds, null);
            Tools.log("read: %s", value.getVi64());
        } catch (Exception e) {
            Tools.log(e.getMessage());
        } finally {
            for(Server server : servers) {
                server.shutdown();
            }
        }



    }

}
