/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other
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

package org.infinispan.interceptors.locking;

import org.infinispan.commands.tx.GMUPrepareCommand;
import org.infinispan.commands.tx.VersionedPrepareCommand;
import org.infinispan.config.Configuration;
import org.infinispan.container.DataContainer;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.container.versioning.EntryVersion;
import org.infinispan.container.versioning.EntryVersionsMap;
import org.infinispan.container.versioning.VersionGenerator;
import org.infinispan.context.Flag;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.scopes.Scope;
import org.infinispan.factories.scopes.Scopes;
import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.transaction.gmu.GMUHelper;
import org.infinispan.transaction.xa.CacheTransaction;
import org.infinispan.util.Util;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.infinispan.transaction.WriteSkewHelper.performWriteSkewCheckAndReturnNewVersions;
import static org.infinispan.transaction.WriteSkewHelper.updateLocalModeCacheEntries;

/**
 * Abstractization for logic related to different clustering modes: replicated or distributed. This implements the <a
 * href="http://en.wikipedia.org/wiki/Bridge_pattern">Bridge</a> pattern as described by the GoF: this plays the role of
 * the <b>Implementor</b> and various LockingInterceptors are the <b>Abstraction</b>.
 *
 * @author Mircea Markus
 * @author Pedro Ruivo
 * @since 5.1
 */
@Scope(Scopes.NAMED_CACHE)
public interface ClusteringDependentLogic {

   Log log = LogFactory.getLog(ClusteringDependentLogic.class);

   boolean localNodeIsOwner(Object key);

   boolean localNodeIsPrimaryOwner(Object key);

   void commitEntry(CacheEntry entry, EntryVersion newVersion, boolean skipOwnershipCheck);

   Collection<Address> getInvolvedNodes(CacheTransaction cacheTransaction);

   EntryVersionsMap createNewVersionsAndCheckForWriteSkews(VersionGenerator versionGenerator, TxInvocationContext context, VersionedPrepareCommand prepareCommand);

   /**
    * performs the read set validation
    *
    * @param context          the transaction context
    * @param prepareCommand   the prepare command
    */
   void performReadSetValidation(TxInvocationContext context, GMUPrepareCommand prepareCommand);

   Address getAddress();

   Collection<Address> getWriteOwners(CacheTransaction cacheTransaction);


   /**
    * This logic is used when a changing a key affects all the nodes in the cluster, e.g. int the replicated,
    * invalidated and local cache modes.
    */
   public static class AllNodesLogic implements ClusteringDependentLogic {

      protected DataContainer dataContainer;

      private RpcManager rpcManager;

      @Inject
      public void init(DataContainer dc, RpcManager rpcManager) {
         this.dataContainer = dc;
         this.rpcManager = rpcManager;
      }

      @Override
      public boolean localNodeIsOwner(Object key) {
         return true;
      }

      @Override
      public boolean localNodeIsPrimaryOwner(Object key) {
         return rpcManager == null || rpcManager.getTransport().isCoordinator();
      }

      @Override
      public void commitEntry(CacheEntry entry, EntryVersion newVersion, boolean skipOwnershipCheck) {
         entry.commit(dataContainer, newVersion);
      }

      @Override
      public Collection<Address> getInvolvedNodes(CacheTransaction cacheTransaction) {
         return null;
      }

      @Override
      public Collection<Address> getWriteOwners(CacheTransaction cacheTransaction) {
         return null;
      }

      @Override
      public Address getAddress() {
         return rpcManager.getAddress();
      }

      @Override
      public EntryVersionsMap createNewVersionsAndCheckForWriteSkews(VersionGenerator versionGenerator, TxInvocationContext context, VersionedPrepareCommand prepareCommand) {
         // In REPL mode, this happens if we are the coordinator.
         if (rpcManager.getTransport().isCoordinator()) {
            // Perform a write skew check on each entry.
            EntryVersionsMap uv = performWriteSkewCheckAndReturnNewVersions(prepareCommand, dataContainer,
                                                                            versionGenerator, context,
                                                                            this);
            context.getCacheTransaction().setUpdatedEntryVersions(uv);
            updateLocalModeCacheEntries(versionGenerator, prepareCommand, context);
            return uv;
         } else if (prepareCommand.getModifications().length == 0) {
            // For situations when there's a local-only put in the prepare,
            // simply add an empty entry version map. This works because when
            // a local-only put is executed, this is not added to the prepare
            // modification list.
            context.getCacheTransaction().setUpdatedEntryVersions(new EntryVersionsMap());
         }
         updateLocalModeCacheEntries(versionGenerator, prepareCommand, context);
         return null;
      }

      @Override
      public void performReadSetValidation(TxInvocationContext context, GMUPrepareCommand prepareCommand) {
         if (rpcManager.getTransport().isCoordinator()) {
            GMUHelper.performReadSetValidation(prepareCommand, dataContainer, this);
         }
      }
   }

   public static class DistributionLogic implements ClusteringDependentLogic {

      protected DistributionManager dm;
      protected DataContainer dataContainer;
      protected Configuration configuration;
      private RpcManager rpcManager;

      @Inject
      public void init(DistributionManager dm, DataContainer dataContainer, Configuration configuration, RpcManager rpcManager) {
         this.dm = dm;
         this.dataContainer = dataContainer;
         this.configuration = configuration;
         this.rpcManager = rpcManager;
      }

      @Override
      public boolean localNodeIsOwner(Object key) {
         return dm.getLocality(key).isLocal();
      }

      @Override
      public Address getAddress() {
         return rpcManager.getAddress();
      }

      @Override
      public boolean localNodeIsPrimaryOwner(Object key) {
         final Address address = rpcManager.getAddress();
         final boolean result = dm.getPrimaryLocation(key).equals(address);
         log.tracef("My address is %s. Am I main owner? - %b", address, result);
         return result;
      }

      @Override
      public void commitEntry(CacheEntry entry, EntryVersion newVersion, boolean skipOwnershipCheck) {
         boolean doCommit = true;
         // ignore locality for removals, even if skipOwnershipCheck is not true
         if (!skipOwnershipCheck && !entry.isRemoved() && !localNodeIsOwner(entry.getKey())) {
            if (configuration.isL1CacheEnabled()) {
               dm.transformForL1(entry);
            } else {
               doCommit = false;
            }
         }
         if (doCommit)
            entry.commit(dataContainer, newVersion);
         else
            entry.rollback();
      }

      @Override
      public Collection<Address> getInvolvedNodes(CacheTransaction cacheTransaction) {
         Collection<Address> writeOwners = getWriteOwners(cacheTransaction);
         if (writeOwners == null) {
            return null;
         }
         Collection<Address> readOwners = getReadOwners(cacheTransaction);
         if (readOwners.isEmpty()) {
            return writeOwners;
         }
         Set<Address> addressSet = new HashSet<Address>(writeOwners);
         addressSet.addAll(readOwners);
         return addressSet;
      }

      @Override
      public EntryVersionsMap createNewVersionsAndCheckForWriteSkews(VersionGenerator versionGenerator, TxInvocationContext context, VersionedPrepareCommand prepareCommand) {
         // Perform a write skew check on mapped entries.
         EntryVersionsMap uv = performWriteSkewCheckAndReturnNewVersions(prepareCommand, dataContainer,
                                                                         versionGenerator, context,
                                                                         this);

         CacheTransaction cacheTransaction = context.getCacheTransaction();
         EntryVersionsMap uvOld = cacheTransaction.getUpdatedEntryVersions();
         if (uvOld != null && !uvOld.isEmpty()) {
            uvOld.putAll(uv);
            uv = uvOld;
         }
         cacheTransaction.setUpdatedEntryVersions(uv);
         updateLocalModeCacheEntries(versionGenerator, prepareCommand, context);
         return (uv.isEmpty()) ? null : uv;
      }

      @Override
      public void performReadSetValidation(TxInvocationContext context, GMUPrepareCommand prepareCommand) {
         GMUHelper.performReadSetValidation(prepareCommand, dataContainer, this);
      }

      @Override
      public Collection<Address> getWriteOwners(CacheTransaction cacheTransaction) {
         Collection<Object> affectedKeys = Util.getAffectedKeys(cacheTransaction.getModifications(), null);
         if (affectedKeys == null) {
            return null;
         }
         return dm.getAffectedNodes(affectedKeys);
      }

      private Collection<Address> getReadOwners(CacheTransaction cacheTransaction) {
         Collection<Object> readKeys = cacheTransaction.getReadKeys();
         if (readKeys == null) {
            return Collections.emptyList();
         }
         return dm.getAffectedNodes(readKeys);
      }
   }

   /**
    * Logic for total order protocol in replicated mode
    */
   public static class TotalOrderAllNodesLogic extends AllNodesLogic {

      @Override
      public boolean localNodeIsPrimaryOwner(Object key) {
         //no lock acquisition in total order
         return false;
      }

      @Override
      public EntryVersionsMap createNewVersionsAndCheckForWriteSkews(VersionGenerator versionGenerator,
                                                                     TxInvocationContext context,
                                                                     VersionedPrepareCommand prepareCommand) {
         updateLocalModeCacheEntries(versionGenerator, prepareCommand, context);
         if (context.isOriginLocal()) {
            throw new IllegalStateException("This must not be reached");
         }

         EntryVersionsMap updatedVersionMap = new EntryVersionsMap();

         if (!context.hasFlag(Flag.SKIP_WRITE_SKEW_CHECK)) {
            updatedVersionMap = performWriteSkewCheckAndReturnNewVersions(prepareCommand, dataContainer,
                                                                          versionGenerator, context,
                                                                          this);
            context.getCacheTransaction().setUpdatedEntryVersions(updatedVersionMap);
         } else {
            updatedVersionMap.putAll(context.getCacheTransaction().getUpdatedEntryVersions());
         }

         return updatedVersionMap;
      }

      @Override
      public void performReadSetValidation(TxInvocationContext context, GMUPrepareCommand prepareCommand) {
         GMUHelper.performReadSetValidation(prepareCommand, dataContainer, this);
      }
   }

   /**
    * Logic for the total order protocol in distribution mode
    */
   public static class TotalOrderDistributionLogic extends DistributionLogic {

      @Override
      public boolean localNodeIsPrimaryOwner(Object key) {
         return localNodeIsOwner(key);
      }

      @Override
      public void commitEntry(CacheEntry entry, EntryVersion newVersion, boolean skipOwnershipCheck) {
         boolean doCommit = true;
         // ignore locality for removals, even if skipOwnershipCheck is not true
         if (!skipOwnershipCheck && !entry.isRemoved() && !localNodeIsOwner(entry.getKey())) {
            if (configuration.isL1CacheEnabled()) {
               dm.transformForL1(entry);
            } else {
               doCommit = false;
            }
         }
         if (doCommit) {
            entry.commit(dataContainer, newVersion);
         } else {
            entry.rollback();
         }
      }

      @Override
      public EntryVersionsMap createNewVersionsAndCheckForWriteSkews(VersionGenerator versionGenerator, TxInvocationContext context, VersionedPrepareCommand prepareCommand) {
         EntryVersionsMap updatedVersionMap = new EntryVersionsMap();

         if (!context.hasFlag(Flag.SKIP_WRITE_SKEW_CHECK)) {
            updatedVersionMap = performWriteSkewCheckAndReturnNewVersions(prepareCommand, dataContainer,
                                                                          versionGenerator, context,
                                                                          this);
            context.getCacheTransaction().setUpdatedEntryVersions(updatedVersionMap);
         } else {
            updatedVersionMap.putAll(context.getCacheTransaction().getUpdatedEntryVersions());
         }

         return updatedVersionMap;
      }

      @Override
      public void performReadSetValidation(TxInvocationContext context, GMUPrepareCommand prepareCommand) {
         GMUHelper.performReadSetValidation(prepareCommand, dataContainer, this);
      }
   }
}
