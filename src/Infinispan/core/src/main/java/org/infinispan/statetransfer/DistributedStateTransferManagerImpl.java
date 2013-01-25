/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.infinispan.statetransfer;

import org.infinispan.CacheException;
import org.infinispan.commands.write.InvalidateCommand;
import org.infinispan.context.InvocationContext;
import org.infinispan.dataplacement.ClusterSnapshot;
import org.infinispan.dataplacement.lookup.ObjectLookup;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.distribution.ch.ConsistentHashHelper;
import org.infinispan.distribution.ch.DataPlacementConsistentHash;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.jmx.annotations.MBean;
import org.infinispan.loaders.CacheStore;
import org.infinispan.remoting.transport.Address;
import org.infinispan.statetransfer.totalorder.TotalOrderDistributedStateTransferTask;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.List;

import static org.infinispan.context.Flag.CACHE_MODE_LOCAL;
import static org.infinispan.context.Flag.SKIP_LOCKING;

/**
 * The distributed mode implementation of {@link StateTransferManager}
 *
 * @author Manik Surtani
 * @author Vladimir Blagojevic
 * @author Mircea.Markus@jboss.com
 * @author Bela Ban
 * @author Dan Berindei &lt;dan@infinispan.org&gt;
 * @author Zhongmiao Li
 * @author Pedro Ruivo
 * @since 4.0
 */
@MBean(objectName = "DistributedStateTransferManager", description = "Component that handles state transfer in distributed mode")
public class DistributedStateTransferManagerImpl extends BaseStateTransferManagerImpl {
   private static final Log log = LogFactory.getLog(DistributedStateTransferManagerImpl.class);

   protected DistributionManager dm;

   private DataPlacementConsistentHash dataPlacementConsistentHash;

   /**
    * Default constructor
    */
   public DistributedStateTransferManagerImpl() {
      super();
   }

   @Inject
   public void init(DistributionManager dm) {
      this.dm = dm;
   }


   @Override
   protected BaseStateTransferTask createStateTransferTask(int viewId, List<Address> members, boolean initialView, int replicationDegree) {
      if (isTotalOrder()) {
         return new TotalOrderDistributedStateTransferTask(rpcManager, configuration, dataContainer,
                                                           this, dm, stateTransferLock, cacheNotifier, viewId, members,
                                                           chOld, chNew, initialView, transactionTable, totalOrderManager,
                                                           replicationDegree);
      } else {
         return new DistributedStateTransferTask(rpcManager, configuration, dataContainer,
                                                 this, dm, stateTransferLock, cacheNotifier, viewId, members, chOld,
                                                 chNew, initialView, transactionTable, replicationDegree);
      }
   }

   @Override
   protected long getTimeout() {
      return configuration.getRehashWaitTime();
   }


   @Override
   protected ConsistentHash createConsistentHash(List<Address> members, int replicationDegree) {
      if (replicationDegree > 0) {
         //change in replication degree... return CH old
         return chOld;
      }
      ConsistentHash defaultHash = ConsistentHashHelper.createConsistentHash(configuration, members);
      if (isDataPlacementConsistentHash()) {
         dataPlacementConsistentHash.setDefault(defaultHash);
         return dataPlacementConsistentHash;
      } else {
         return defaultHash;
      }
   }

   @Override
   public void commitView(int viewId) {
      dataPlacementConsistentHash = null; //TODO check: if a node fails, it will create a default consistent hash,
      //TODO: and it puts the keys back in their original owner (home)
      super.commitView(viewId);
   }

   public void addObjectLookup(Address address, ObjectLookup objectLookup){
      if (dataPlacementConsistentHash == null) {
         log.errorf("Trying to add the Object Lookup from %s but the Data Placement Consistent Hash is null", address);
         return;
      } else {
         if (log.isDebugEnabled()) {
            log.debugf("Add Object Lookup from %s", address);
         }
      }

      dataPlacementConsistentHash.addObjectLookup(address, objectLookup);
   }

   public void createDataPlacementConsistentHashing(ClusterSnapshot clusterSnapshot){
      dataPlacementConsistentHash = new DataPlacementConsistentHash(clusterSnapshot);
   }

   public void invalidateKeys(List<Object> keysToRemove) {
      try {
         if (keysToRemove.size() > 0) {
            InvalidateCommand invalidateCmd = cf.buildInvalidateFromL1Command(true, keysToRemove);
            InvocationContext ctx = icc.createNonTxInvocationContext();
            ctx.setFlags(CACHE_MODE_LOCAL, SKIP_LOCKING);
            interceptorChain.invoke(ctx, invalidateCmd);

            log.debugf("Invalidated %d keys, data container now has %d keys", keysToRemove.size(), dataContainer.size(null));
            log.tracef("Invalidated keys: %s", keysToRemove);
         }
      } catch (CacheException e) {
         log.failedToInvalidateKeys(e);
      }
   }

   @Override
   public CacheStore getCacheStoreForStateTransfer() {
      if (cacheLoaderManager == null || !cacheLoaderManager.isEnabled() || cacheLoaderManager.isShared())
         return null;
      return cacheLoaderManager.getCacheStore();
   }

   @Override
   public boolean isLocationInDoubt(Object key) {
      return isStateTransferInProgress() && !chOld.isKeyLocalToAddress(getAddress(), key, configuration.getNumOwners())
            && chNew.isKeyLocalToAddress(getAddress(), key, configuration.getNumOwners());
   }

   private boolean isDataPlacementConsistentHash() {
      return dataPlacementConsistentHash != null;
   }
}

