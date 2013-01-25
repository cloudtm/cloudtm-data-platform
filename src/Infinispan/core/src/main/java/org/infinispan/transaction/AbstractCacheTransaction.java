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

package org.infinispan.transaction;

import org.infinispan.commands.write.WriteCommand;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.container.versioning.EntryVersion;
import org.infinispan.container.versioning.EntryVersionsMap;
import org.infinispan.container.versioning.VersionGenerator;
import org.infinispan.container.versioning.gmu.GMUVersionGenerator;
import org.infinispan.remoting.transport.Address;
import org.infinispan.transaction.xa.CacheTransaction;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.util.concurrent.ConcurrentHashSet;
import org.infinispan.util.concurrent.ConcurrentMapFactory;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.infinispan.transaction.gmu.GMUHelper.toGMUVersionGenerator;

/**
 * Base class for local and remote transaction. Impl note: The aggregated modification list and lookedUpEntries are not
 * instantiated here but in subclasses. This is done in order to take advantage of the fact that, for remote
 * transactions we already know the size of the modifications list at creation time.
 *
 * @author Mircea.Markus@jboss.com
 * @author Galder Zamarreño
 * @author Pedro Ruivo
 * @author Sebastiano Peluso
 * @since 4.2
 */
public abstract class AbstractCacheTransaction implements CacheTransaction {

   protected final GlobalTransaction tx;
   private static Log log = LogFactory.getLog(AbstractCacheTransaction.class);
   private static final boolean trace = log.isTraceEnabled();
   private static final int INITIAL_LOCK_CAPACITY = 4;

   protected List<WriteCommand> modifications;
   protected Map<Object, CacheEntry> lookedUpEntries;
   protected Set<Object> affectedKeys = null;
   protected Set<Object> lockedKeys;
   protected Set<Object> backupKeyLocks = null;
   private boolean txComplete = false;
   protected volatile boolean prepared;
   private volatile boolean needToNotifyWaiters = false;
   final int viewId;

   private EntryVersionsMap updatedEntryVersions;

   private EntryVersion transactionVersion;

   public AbstractCacheTransaction(GlobalTransaction tx, int viewId) {
      this.tx = tx;
      this.viewId = viewId;
   }

   @Override
   public GlobalTransaction getGlobalTransaction() {
      return tx;
   }

   @Override
   public List<WriteCommand> getModifications() {
      return modifications;
   }

   public void setModifications(WriteCommand[] modifications) {
      this.modifications = Arrays.asList(modifications);
   }

   @Override
   public Map<Object, CacheEntry> getLookedUpEntries() {
      return lookedUpEntries;
   }

   @Override
   public CacheEntry lookupEntry(Object key) {
      if (lookedUpEntries == null) return null;
      return lookedUpEntries.get(key);
   }

   @Override
   public void removeLookedUpEntry(Object key) {
      if (lookedUpEntries != null) lookedUpEntries.remove(key);
   }

   @Override
   public void clearLookedUpEntries() {
      lookedUpEntries = null;
   }

   @Override
   public boolean ownsLock(Object key) {
      return getLockedKeys().contains(key);
   }

   @Override
   public void notifyOnTransactionFinished() {
      if (trace) log.tracef("Transaction %s has completed, notifying listening threads.", tx);
      txComplete = true; //this one is cheap but does not guarantee visibility
      if (needToNotifyWaiters) {
         synchronized (this) {
            txComplete = true; //in this case we want to guarantee visibility to other threads
            this.notifyAll();
         }
      }
   }

   @Override
   public boolean waitForLockRelease(Object key, long lockAcquisitionTimeout) throws InterruptedException {
      if (txComplete) return true; //using an unsafe optimisation: if it's true, we for sure have the latest read of the value without needing memory barriers
      final boolean potentiallyLocked = hasLockOrIsLockBackup(key);
      if (trace) log.tracef("Transaction gtx=%s potentially locks key %s? %s", tx, key, potentiallyLocked);
      if (potentiallyLocked) {
         synchronized (this) {
            // Check again after acquiring a lock on the monitor that the transaction has completed.
            // If it has completed, all of its locks would have been released.
            needToNotifyWaiters = true;
            //The order in which these booleans are verified is critical as we take advantage of it to avoid otherwise needed locking
            if (txComplete) {
               needToNotifyWaiters = false;
               return true;
            }
            this.wait(lockAcquisitionTimeout);

            // Check again in case of spurious thread signalling
            return txComplete;
         }
      }
      return true;
   }

   @Override
   public int getViewId() {
      return viewId;
   }

   @Override
   public void addBackupLockForKey(Object key) {
      if (backupKeyLocks == null) backupKeyLocks = createSet(INITIAL_LOCK_CAPACITY);
      backupKeyLocks.add(key);
   }

   public void registerLockedKey(Object key) {
      if (lockedKeys == null) lockedKeys = createSet(INITIAL_LOCK_CAPACITY);
      if (trace) log.tracef("Registering locked key: %s", key);
      lockedKeys.add(key);
   }

   @Override
   public Set<Object> getLockedKeys() {
      return lockedKeys == null ? Collections.emptySet() : lockedKeys;
   }

   @Override
   public void clearLockedKeys() {
      if (trace) log.tracef("Clearing locked keys: %s", lockedKeys);
      lockedKeys = null;
   }

   private boolean hasLockOrIsLockBackup(Object key) {
      return (lockedKeys != null && lockedKeys.contains(key)) || (backupKeyLocks != null && backupKeyLocks.contains(key));
   }

   public Set<Object> getAffectedKeys() {
      return affectedKeys == null ? Collections.emptySet() : affectedKeys;
   }

   public void addAffectedKey(Object key) {
      initAffectedKeys();
      affectedKeys.add(key);
   }

   public void addAllAffectedKeys(Collection<Object> keys) {
      initAffectedKeys();
      for (Object key : keys) {
         affectedKeys.add(key);
      }
   }

   private void initAffectedKeys() {
      if (affectedKeys == null) affectedKeys = createSet(INITIAL_LOCK_CAPACITY);
   }

   @Override
   public EntryVersionsMap getUpdatedEntryVersions() {
      return updatedEntryVersions;
   }

   @Override
   public void setUpdatedEntryVersions(EntryVersionsMap updatedEntryVersions) {
      this.updatedEntryVersions = updatedEntryVersions;
   }

   @Override
   public void addReadKey(Object key) {
      // No-op
   }

   @Override
   public boolean keyRead(Object key) {
      return false;
   }

   @Override
   public void markPrepareSent() {
      //no-op
   }

   @Override
   public boolean wasPrepareSent() {
      return false;  // no-op
   }

   @Override
   public EntryVersion calculateVersionToRead(VersionGenerator versionGenerator) {
      GMUVersionGenerator gmuVersionGenerator = toGMUVersionGenerator(versionGenerator);
      return gmuVersionGenerator.calculateMaxVersionToRead(transactionVersion, getReadFrom());
   }

   @Override
   public Collection<Object> getReadKeys() {
      return Collections.emptyList();
   }

   @Override
   public void addReadFrom(Address address) {
      //no-op
   }

   @Override
   public Set<Address> getReadFrom() {
      return Collections.emptySet();
   }

   @Override
   public void setTransactionVersion(EntryVersion version) {
      if (log.isDebugEnabled()) {
         log.debugf("[%s] new transaction version: %s", tx.prettyPrint(), version);
      }
      transactionVersion = version;
   }

   @Override
   public EntryVersion getTransactionVersion() {
      if (log.isDebugEnabled()) {
         log.debugf("[%s] get transaction version: %s", tx.prettyPrint(), transactionVersion);
      }
      return transactionVersion;
   }

   @Override
   public boolean hasAlreadyReadOnThisNode() {
      return false;
   }

   @Override
   public void setAlreadyReadOnThisNode(boolean value) {
      //no-op
   }

   protected final Map<Object, CacheEntry> createMapEntries(int size) {
      return ConcurrentMapFactory.makeConcurrentMap(size);
   }

   protected final Map<Object, CacheEntry> createMapEntries(Map<Object, CacheEntry> map) {
      Map<Object, CacheEntry> newMap = ConcurrentMapFactory.makeConcurrentMap(map.size());
      newMap.putAll(map);
      return newMap;
   }

   protected final Set<Object> createSet(int size) {
      return new ConcurrentHashSet<Object>(size);
   }
}
