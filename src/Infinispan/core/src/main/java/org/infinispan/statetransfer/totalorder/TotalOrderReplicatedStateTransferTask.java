/*
 * INESC-ID, Instituto de Engenharia de Sistemas e Computadores Investigação e Desevolvimento em Lisboa
 * Copyright 2013 INESC-ID and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3.0 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.statetransfer.totalorder;

import org.infinispan.config.Configuration;
import org.infinispan.container.DataContainer;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.notifications.cachelistener.CacheNotifier;
import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.statetransfer.ReplicatedStateTransferManagerImpl;
import org.infinispan.statetransfer.ReplicatedStateTransferTask;
import org.infinispan.statetransfer.StateTransferLock;
import org.infinispan.transaction.TxDependencyLatch;
import org.infinispan.transaction.totalorder.TotalOrderManager;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.Collection;
import java.util.Set;

/**
 * Task the pushes the state for the new node and it is aware that total order broadcast based protocol is in use
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class TotalOrderReplicatedStateTransferTask extends ReplicatedStateTransferTask {

   private static final Log log = LogFactory.getLog(TotalOrderReplicatedStateTransferTask.class);

   private final TotalOrderManager totalOrderManager;

   public TotalOrderReplicatedStateTransferTask(RpcManager rpcManager, Configuration configuration,
                                                DataContainer dataContainer,
                                                ReplicatedStateTransferManagerImpl stateTransferManager,
                                                StateTransferLock stateTransferLock, CacheNotifier cacheNotifier,
                                                int newViewId, Collection<Address> members, ConsistentHash chOld,
                                                ConsistentHash chNew, boolean initialView, TotalOrderManager totalOrderManager) {
      super(rpcManager, configuration, dataContainer, stateTransferManager, stateTransferLock, cacheNotifier, newViewId,
            members, chOld, chNew, initialView);
      this.totalOrderManager = totalOrderManager;
   }

   @Override
   protected void beforeStartPushing() {
      Set<TxDependencyLatch> pendingTransaction = totalOrderManager.getPendingCommittingTransaction();

      if (log.isTraceEnabled()) {
         log.tracef("Waiting for pending remote transaction before pushing the data. Transaction are %s",pendingTransaction);
      }

      for (TxDependencyLatch txDependencyLatch : pendingTransaction) {
         try {
            txDependencyLatch.await();
         } catch (InterruptedException e) {
            log.warnf("Interrupted exception caught while waiting for %s", txDependencyLatch);
         }
      }
   }
}
