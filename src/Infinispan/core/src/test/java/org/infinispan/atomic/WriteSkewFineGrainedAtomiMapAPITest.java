package org.infinispan.atomic;

import org.infinispan.config.Configuration;
import org.infinispan.configuration.cache.VersioningScheme;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.TransactionMode;
import org.infinispan.util.concurrent.IsolationLevel;
import org.testng.annotations.Test;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
@Test(groups = "functional", testName = "atomic.WriteSkewFineGrainedAtomiMapAPITest")
public class WriteSkewFineGrainedAtomiMapAPITest extends FineGrainedAtomicMapAPITest {

   @Override
   protected void createCacheManagers() throws Throwable {
      Configuration c = getDefaultClusteredConfig(Configuration.CacheMode.REPL_SYNC, true)
            .fluent()
            .transaction()
            .transactionMode(TransactionMode.TRANSACTIONAL)
            .lockingMode(LockingMode.OPTIMISTIC)
            .locking().isolationLevel(IsolationLevel.REPEATABLE_READ)
            .locking().lockAcquisitionTimeout(100l)
            .locking().writeSkewCheck(true)
            .versioning().enable().versioningScheme(VersioningScheme.SIMPLE)
            .build();
      createClusteredCaches(2, "atomic", c);
   }
}
