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
package org.infinispan.util.concurrent.locks.containers;

import org.infinispan.util.concurrent.ConcurrentMapFactory;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * An abstract lock container that creates and maintains a new lock per entry
 *
 * @author Manik Surtani
 * @since 4.0
 */
public abstract class AbstractPerEntryLockContainer<L extends Lock> extends AbstractLockContainer<L> {

   protected final ConcurrentMap<Object, L> locks;

   protected AbstractPerEntryLockContainer(int concurrencyLevel) {
      locks = ConcurrentMapFactory.makeConcurrentMap(16, concurrencyLevel);
   }

   @Override
   public int getNumLocksHeld() {
      return locks.size();
   }

   @Override
   public int size() {
      return locks.size();
   }

   @Override
   public L acquireExclusiveLock(Object lockOwner, Object key, long timeout, TimeUnit unit) throws InterruptedException {
      return acquire(lockOwner, key, timeout, unit, false);
   }

   @Override
   public L acquireShareLock(Object lockOwner, Object key, long timeout, TimeUnit unit) throws InterruptedException {
      return acquire(lockOwner, key, timeout, unit, true);
   }

   @Override
   public void releaseExclusiveLock(Object lockOwner, Object key) {
      L l = locks.get(key);
      if (l != null) unlockExclusive(l, lockOwner);
   }

   @Override
   public void releaseShareLock(Object lockOwner, Object key) {
      L l = locks.get(key);
      if (l != null) unlockShare(l, lockOwner);
   }

   @Override
   public int getLockId(Object key) {
      return System.identityHashCode(getExclusiveLock(key));
   }

   protected abstract L newLock();

   protected final L getLockFromMap(Object key, boolean putIfAbsent) {
      // this is an optimisation.  It is not foolproof as we may still be creating new locks unnecessarily (thrown away
      // when we do a putIfAbsent) but it minimises the chances somewhat, for the cost of an extra CHM get.
      L lock = locks.get(key);
      if (lock == null && putIfAbsent) {
         lock = newLock();
         L existingLock = locks.putIfAbsent(key, lock);
         if (existingLock != null) lock = existingLock;
      }
      return lock;
   }

   private L acquire(Object lockOwner, Object key, long timeout, TimeUnit unit, boolean share) throws InterruptedException {
      while (true) {
         L lock = share ? getShareLock(key) : getExclusiveLock(key);
         boolean locked;
         try {
            locked = share ?
                  tryShareLock(lock, timeout, unit, lockOwner) :
                  tryExclusiveLock(lock, timeout, unit, lockOwner);
         } catch (InterruptedException ie) {
            if (share) {
               safeShareRelease(lock, lockOwner);
            } else {
               safeExclusiveRelease(lock, lockOwner);
            }
            throw ie;
         } catch (Throwable th) {
            locked = false;
         }
         if (locked) {
            // lock acquired.  Now check if it is the *correct* lock!
            L existingLock = locks.putIfAbsent(key, lock);
            if (existingLock != null && existingLock != lock) {
               // we have the wrong lock!  Unlock and retry.
               if (share) {
                  safeShareRelease(lock, lockOwner);
               } else {
                  safeExclusiveRelease(lock, lockOwner);
               }
            } else {
               // we got the right lock.
               return lock;
            }
         } else {
            // we couldn't acquire the lock within the timeout period
            return null;
         }
      }
   }
}
