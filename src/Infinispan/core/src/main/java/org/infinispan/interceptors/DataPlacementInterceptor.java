package org.infinispan.interceptors;

import org.infinispan.distribution.DistributionManager;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.interceptors.base.CommandInterceptor;

public class DataPlacementInterceptor extends CommandInterceptor {
   
   private DistributionManager distributionManager;
   
	
   @Inject
   public void init(DistributionManager distributionManager) {
       this.distributionManager = distributionManager;
   }
   
   
}
