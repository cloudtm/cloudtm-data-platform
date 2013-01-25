package org.infinispan.util.concurrent.locks.containers.readwrite;

import org.infinispan.util.concurrent.locks.OwnableReentrantReadWriteLock;
import org.infinispan.util.concurrent.locks.containers.AbstractPerEntryLockContainer;

import java.util.concurrent.TimeUnit;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class OwnableReentrantPerEntryReadWriteLockContainer extends AbstractPerEntryLockContainer<OwnableReentrantReadWriteLock> {


   public OwnableReentrantPerEntryReadWriteLockContainer(int concurrencyLevel) {
      super(concurrencyLevel);
   }

   @Override
   protected OwnableReentrantReadWriteLock newLock() {
      return new OwnableReentrantReadWriteLock();
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
      OwnableReentrantReadWriteLock l = getLockFromMap(key, false);
      return l != null && owner.equals(l.getOwner());
   }

   @Override
   public boolean ownsShareLock(Object key, Object owner) {
      OwnableReentrantReadWriteLock l = getLockFromMap(key, false);
      return l.ownsShareLock(owner);
   }

   @Override
   public boolean isExclusiveLocked(Object key) {
      OwnableReentrantReadWriteLock l = getLockFromMap(key, false);
      return l != null && l.isLocked();
   }

   @Override
   public boolean isSharedLocked(Object key) {
      OwnableReentrantReadWriteLock l = getLockFromMap(key, false);
      return l != null && l.isShareLocked();
   }

   @Override
   public OwnableReentrantReadWriteLock getExclusiveLock(Object key) {
      return getLockFromMap(key, true);
   }

   @Override
   public OwnableReentrantReadWriteLock getShareLock(Object key) {
      return getLockFromMap(key, true);
   }
}
