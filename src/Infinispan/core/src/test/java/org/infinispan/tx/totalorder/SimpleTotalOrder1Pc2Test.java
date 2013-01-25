package org.infinispan.tx.totalorder;

import org.testng.annotations.Test;

/**
 * Also run a test with 3 nodes as the RPC manager might send an unicast (vs broadcast)
 * if the broadcast target size == 1.
 *
 * @author mircea.markus@jboss.com
 * @since 5.2.0
 */
@Test(groups = "functional", testName = "tx.totalorder.SimpleTotalOrderOnePhase2Test")
public class SimpleTotalOrder1Pc2Test extends SimpleTotalOrder1PcTest {

   public SimpleTotalOrder1Pc2Test() {
      this.clusterSize = 3;
   }
}
