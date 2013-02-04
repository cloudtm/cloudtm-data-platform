/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
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
package org.infinispan.distribution;

import org.infinispan.commands.remote.ClusteredGetCommand;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.container.entries.gmu.InternalGMUCacheEntry;
import org.infinispan.container.entries.gmu.InternalGMUCacheValue;
import org.infinispan.container.versioning.VersionGenerator;
import org.infinispan.container.versioning.gmu.GMUVersion;
import org.infinispan.container.versioning.gmu.GMUVersionGenerator;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.SingleKeyNonTxInvocationContext;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.dataplacement.ClusterSnapshot;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.jmx.annotations.MBean;
import org.infinispan.remoting.responses.ClusteredGetResponseValidityFilter;
import org.infinispan.remoting.responses.Response;
import org.infinispan.remoting.responses.SuccessfulResponse;
import org.infinispan.remoting.rpc.ResponseFilter;
import org.infinispan.remoting.rpc.ResponseMode;
import org.infinispan.remoting.transport.Address;
import org.infinispan.transaction.gmu.CommitLog;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.infinispan.transaction.gmu.GMUHelper.*;

/**
 * The distribution manager implementation for the GMU protocol
 *
 * @author Pedro Ruivo
 * @author Sebastiano Peluso
 * @since 5.2
 */
@MBean(objectName = "GMUDistributionManager", description = "Component that handles distribution of content across a cluster")
public class GMUDistributionManagerImpl extends DistributionManagerImpl {

   private static final Log log = LogFactory.getLog(GMUDistributionManagerImpl.class);

   private GMUVersionGenerator versionGenerator;
   private CommitLog commitLog;

   /**
    * Default constructor
    */
   public GMUDistributionManagerImpl() {}

   @Inject
   public void setVersionGeneratorAndCommitLog(VersionGenerator versionGenerator, CommitLog commitLog) {
      this.versionGenerator = toGMUVersionGenerator(versionGenerator);
      this.commitLog = commitLog;
   }

   @Override
   public InternalCacheEntry retrieveFromRemoteSource(Object key, InvocationContext ctx, boolean acquireRemoteLock) throws Exception {
      if (ctx instanceof SingleKeyNonTxInvocationContext) {
         return retrieveSingleKeyFromRemoteSource(key, (SingleKeyNonTxInvocationContext) ctx);
      } else if (ctx instanceof TxInvocationContext) {
         return retrieveTransactionalGetFromRemoteSource(key, (TxInvocationContext) ctx, acquireRemoteLock);
      }
      throw new IllegalStateException("Only handles transaction context or single key gets");
   }

   private InternalCacheEntry retrieveTransactionalGetFromRemoteSource(Object key, TxInvocationContext txInvocationContext,
                                                                       boolean acquireRemoteLock) {
      GlobalTransaction gtx = acquireRemoteLock ? txInvocationContext.getGlobalTransaction() : null;

      List<Address> targets = new ArrayList<Address>(locate(key));
      // if any of the recipients has left the cluster since the command was issued, just don't wait for its response
      targets.retainAll(rpcManager.getTransport().getMembers());

      Collection<Address> alreadyReadFrom = txInvocationContext.getAlreadyReadFrom();
      GMUVersion transactionVersion = toGMUVersion(txInvocationContext.getTransactionVersion());

      BitSet alreadyReadFromMask;

      if (alreadyReadFrom == null) {
         alreadyReadFromMask = null;
      } else {
         int txViewId = transactionVersion.getViewId();
         ClusterSnapshot clusterSnapshot = versionGenerator.getClusterSnapshot(txViewId);
         alreadyReadFromMask = new BitSet(clusterSnapshot.size());

         for (Address address : alreadyReadFrom) {
            int idx = clusterSnapshot.indexOf(address);
            if (idx != -1) {
               alreadyReadFromMask.set(idx);
            }
         }
      }

      ClusteredGetCommand get = cf.buildGMUClusteredGetCommand(key, txInvocationContext.getFlags(), acquireRemoteLock,
                                                               gtx, transactionVersion, alreadyReadFromMask);

      if(log.isDebugEnabled()) {
         log.debugf("Perform a remote get for transaction %s. %s",
                    txInvocationContext.getGlobalTransaction().prettyPrint(), get);
      }

      ResponseFilter filter = new ClusteredGetResponseValidityFilter(targets, getAddress());
      Map<Address, Response> responses = rpcManager.invokeRemotely(targets, get, ResponseMode.WAIT_FOR_VALID_RESPONSE,
                                                                   configuration.getSyncReplTimeout(), true, filter, false);

      if(log.isDebugEnabled()) {
         log.debugf("Remote get done for transaction %s [key:%s]. response are: %s",
                    txInvocationContext.getGlobalTransaction().prettyPrint(),
                    key, responses);
      }

      if (!responses.isEmpty()) {
         for (Map.Entry<Address,Response> entry : responses.entrySet()) {
            Response r = entry.getValue();
            if (r instanceof SuccessfulResponse) {
               InternalGMUCacheValue gmuCacheValue = convert(((SuccessfulResponse) r).getResponseValue(),
                                                             InternalGMUCacheValue.class);

               InternalGMUCacheEntry gmuCacheEntry = (InternalGMUCacheEntry) gmuCacheValue.toInternalCacheEntry(key);
               txInvocationContext.addKeyReadInCommand(key, gmuCacheEntry);
               txInvocationContext.addReadFrom(entry.getKey());

               if(log.isDebugEnabled()) {
                  log.debugf("Remote Get successful for transaction %s and key %s. Return value is %s",
                             txInvocationContext.getGlobalTransaction().prettyPrint(), key, gmuCacheValue);
               }
               return gmuCacheEntry;
            }
         }
      }

      // TODO If everyone returned null, and the read CH has changed, retry the remote get.
      // Otherwise our get command might be processed by the old owners after they have invalidated their data
      // and we'd return a null even though the key exists on
      return null;
   }

   private InternalCacheEntry retrieveSingleKeyFromRemoteSource(Object key, SingleKeyNonTxInvocationContext ctx) {
      List<Address> targets = new ArrayList<Address>(locate(key));
      // if any of the recipients has left the cluster since the command was issued, just don't wait for its response
      targets.retainAll(rpcManager.getTransport().getMembers());

      ClusteredGetCommand get = cf.buildGMUClusteredGetCommand(key, ctx.getFlags(), false, null,
                                                               toGMUVersion(commitLog.getCurrentVersion()), null);

      if(log.isDebugEnabled()) {
         log.debugf("Perform a single remote get. %s", get);
      }

      ResponseFilter filter = new ClusteredGetResponseValidityFilter(targets, getAddress());
      Map<Address, Response> responses = rpcManager.invokeRemotely(targets, get, ResponseMode.WAIT_FOR_VALID_RESPONSE,
                                                                   configuration.getSyncReplTimeout(), true, filter, false);

      if(log.isDebugEnabled()) {
         log.debugf("Remote get done for single key [key:%s]. response are: %s", key, responses);
      }


      if (!responses.isEmpty()) {
         for (Map.Entry<Address,Response> entry : responses.entrySet()) {
            Response r = entry.getValue();
            if (r == null) {
               continue;
            }
            if (r instanceof SuccessfulResponse) {
               InternalGMUCacheValue gmuCacheValue = convert(((SuccessfulResponse) r).getResponseValue(),
                                                             InternalGMUCacheValue.class);

               InternalGMUCacheEntry gmuCacheEntry = (InternalGMUCacheEntry) gmuCacheValue.toInternalCacheEntry(key);
               ctx.addKeyReadInCommand(key, gmuCacheEntry);

               if(log.isDebugEnabled()) {
                  log.debugf("Remote Get successful for single key %s. Return value is %s",key, gmuCacheValue);
               }
               return gmuCacheEntry;
            }
         }
      }

      // TODO If everyone returned null, and the read CH has changed, retry the remote get.
      // Otherwise our get command might be processed by the old owners after they have invalidated their data
      // and we'd return a null even though the key exists on
      return null;
   }
}
