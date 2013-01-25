package org.infinispan.util.concurrent.locks.containers.readwrite;

import org.infinispan.util.concurrent.locks.OwnableReentrantReadWriteLock;
import org.infinispan.util.concurrent.locks.containers.AbstractStripedLockContainer;

import java.util.concurrent.TimeUnit;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class OwnableReentrantStripedReadWriteLockContainer extends AbstractStripedLockContainer<OwnableReentrantReadWriteLock> {

   private OwnableReentrantReadWriteLock[] sharedLocks;

   public OwnableReentrantStripedReadWriteLockContainer(int concurrencyLevel) {
      initLocks(calculateNumberOfSegments(concurrencyLevel));
   }

   @Override
   protected void initLocks(int numLocks) {
      sharedLocks = new OwnableReentrantReadWriteLock[numLocks];
      for (int i = 0; i < numLocks; i++) sharedLocks[i] = new OwnableReentrantReadWriteLock();
   }

   @Override
   protected void unlockExclusive(OwnableReentrantReadWriteLock toRelease, Object owner) {
      toRelease.unlock(owner);
   }

   @Override
   protected void unlockShare(OwnableReentrantReadWriteLock toRelease, Object owner) {
      toRelease.unlockShare(owner);
   }

   @Override
   protected boolean tryExclusiveLock(OwnableReentrantReadWriteLock lock, long timeout, TimeUnit unit, Object lockOwner) throws InterruptedException {
      return lock.tryLock(lockOwner, timeout, unit);
   }

   @Override
   protected boolean tryShareLock(OwnableReentrantReadWriteLock lock, long timeout, TimeUnit unit, Object lockOwner) throws InterruptedException {
      return lock.tryShareLock(lockOwner, timeout, unit);
   }

   @Override
   public boolean ownsExclusiveLock(Object key, Object owner) {
      OwnableReentrantReadWriteLock lock = getExclusiveLock(key);
      return owner.equals(lock.getOwner());
   }

   @Override
   public boolean ownsShareLock(Object key, Object owner) {
      return getShareLock(key).ownsShareLock(owner);
   }

   @Override
   public boolean isExclusiveLocked(Object key) {
      return getExclusiveLock(key).isLocked();
   }

   @Override
   public boolean isSharedLocked(Object key) {
      return getExclusiveLock(key).isShareLocked();
   }

   @Override
   public OwnableReentrantReadWriteLock getExclusiveLock(Object key) {
      return sharedLocks[hashToIndex(key)];
   }

   @Override
   public OwnableReentrantReadWriteLock getShareLock(Object key) {
      return sharedLocks[hashToIndex(key)];
   }

   @Override
   public int getNumLocksHeld() {
      int i = 0;
      for (OwnableReentrantReadWriteLock l : sharedLocks) if (l.isLocked()) i++;
      return i;
   }

   @Override
   public int size() {
      return sharedLocks.length;
   }
}
