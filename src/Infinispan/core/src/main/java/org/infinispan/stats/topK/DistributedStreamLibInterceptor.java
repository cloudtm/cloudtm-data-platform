package org.infinispan.stats.topK;

import org.infinispan.distribution.DistributionManager;

/**
 * @author Diego Didona
 * @author Pedro Ruivo
 * @since 5.2
 */
public class DistributedStreamLibInterceptor extends StreamLibInterceptor {
   private DistributionManager distributionManager;

   @Override
   protected void start() {
      super.start();
      this.distributionManager = cache.getAdvancedCache().getDistributionManager();
   }

   @Override
   protected boolean isRemote(Object key) {
      return distributionManager != null && !distributionManager.getLocality(key).isLocal();
   }
}
