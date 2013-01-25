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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A per-entry lock container for ReentrantLocks
 *
 * @author Manik Surtani
 * @since 4.0
 */
public class ReentrantPerEntryLockContainer extends AbstractPerEntryLockContainer<ReentrantLock> {

   public ReentrantPerEntryLockContainer(int concurrencyLevel) {
      super(concurrencyLevel);
   }

   @Override
   protected ReentrantLock newLock() {
      return new ReentrantLock();
   }

   @Override
   public boolean ownsExclusiveLock(Object key, Object ignored) {
      ReentrantLock l = getLockFromMap(key, false);
      return l != null && l.isHeldByCurrentThread();
   }

   @Override
   public boolean isExclusiveLocked(Object key) {
      ReentrantLock l = getLockFromMap(key, false);
      return l != null && l.isLocked();
   }

   @Override
   protected void unlockExclusive(ReentrantLock l, Object unused) {
      l.unlock();
   }

   @Override
   protected boolean tryExclusiveLock(ReentrantLock lock, long timeout, TimeUnit unit, Object unused) throws InterruptedException {
      return lock.tryLock(timeout, unit);
   }

   @Override
   protected void unlockShare(ReentrantLock toRelease, Object owner) {
      //no-op
   }

   @Override
   protected boolean tryShareLock(ReentrantLock lock, long timeout, TimeUnit unit, Object lockOwner) throws InterruptedException {
      throw new UnsupportedOperationException();
   }

   @Override
   public ReentrantLock getExclusiveLock(Object key) {
      return getLockFromMap(key, true);
   }
}
