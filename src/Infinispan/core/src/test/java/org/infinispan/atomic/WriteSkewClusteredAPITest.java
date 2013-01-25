package org.infinispan.atomic;

import org.infinispan.config.Configuration;
import org.infinispan.configuration.cache.VersioningScheme;
import org.infinispan.util.concurrent.IsolationLevel;
import org.testng.annotations.Test;

/**
 * Write Skew Check tests for Atomic API in a clustered environment
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
@Test(groups = "functional", testName = "atomic.WriteSkewClusteredAPITest")
public class WriteSkewClusteredAPITest extends ClusteredAPITest {

   @Override
   protected void createCacheManagers() throws Throwable {
      Configuration c = getDefaultClusteredConfig(Configuration.CacheMode.REPL_SYNC, true);
      c.setInvocationBatchingEnabled(true);
      c.setWriteSkewCheck(true);
      c.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      c.setVersioningScheme(VersioningScheme.SIMPLE);
      c.setEnableVersioning(true);
      createClusteredCaches(2, "atomic", c);
   }
}
