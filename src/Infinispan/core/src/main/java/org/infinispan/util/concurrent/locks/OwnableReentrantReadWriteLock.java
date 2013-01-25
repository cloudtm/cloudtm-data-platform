package org.infinispan.util.concurrent.locks;

import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class OwnableReentrantReadWriteLock extends OwnableReentrantLock {

   private static final Log log = LogFactory.getLog(OwnableReentrantReadWriteLock.class);

   private transient final Map<Object, AtomicInteger> readCounters = new HashMap<Object, AtomicInteger>();

   public final int getLockState() {
      return getState();
   }

   public final boolean tryShareLock(Object requestor, long time, TimeUnit unit) throws InterruptedException {
      if (log.isTraceEnabled()) {
         log.tracef("%s tryShareLock(%s)", requestor, System.identityHashCode(this));
      }
      setCurrentRequestor(requestor);
      try {
         return tryAcquireSharedNanos(1, unit.toNanos(time));
      } finally {
         if (log.isTraceEnabled()) {
            log.tracef("%s tryShareLock(%s) => FINISH", requestor, System.identityHashCode(this));
         }
         unsetCurrentRequestor();
      }
   }

   public final void unlockShare(Object requestor) {
      if (log.isTraceEnabled()) {
         log.tracef("%s unlockShare(%s)", requestor, System.identityHashCode(this));
      }
      setCurrentRequestor(requestor);
      try {
         releaseShared(1);
      } catch (IllegalMonitorStateException imse) {
         // ignore?
      } finally {
         if (log.isTraceEnabled()) {
            log.tracef("%s unlockShare(%s) => FINISH", requestor, System.identityHashCode(this));
         }
         unsetCurrentRequestor();
      }
   }

   public final boolean ownsShareLock(Object owner) {
      synchronized (readCounters) {
         return readCounters.containsKey(owner);
      }
   }

   public final boolean isShareLocked() {
      return getState() < 0;
   }

   @Override
   protected int tryAcquireShared(int i) {
      Object requestor = currentRequestor();
      int state = getState();
      if (state <= 0 && compareAndSetState(state, state - 1)) {
         incrementRead(requestor);
         if (log.isTraceEnabled()) {
            log.tracef("%s tryAcquireShared(%s) => SUCCESS", requestor, System.identityHashCode(this));
         }
         return 1;
      } else if (state > 0) {
         if (log.isTraceEnabled()) {
            log.tracef("%s tryAcquireShared(%s) => WRITE_LOCKED (%s)", requestor, System.identityHashCode(this),
                       requestor.equals(getOwner()));
         }
         return requestor.equals(getOwner()) ? 0 : -1 ;
      }
      if (log.isTraceEnabled()) {
         log.tracef("%s tryAcquireShared(%s) => FAILED", requestor, System.identityHashCode(this));
      }
      return -1;
   }

   @Override
   protected boolean tryReleaseShared(int i) {
      if (!decrementRead(currentRequestor())) {
         if (log.isTraceEnabled()) {
            log.tracef("%s tryReleaseShared(%s) => FAILED (Not Onwer)", currentRequestor(), System.identityHashCode(this));
         }
         return false;
      }
      while (true) {
         int state = getState();
         if (compareAndSetState(state, state + 1)) {
            if (log.isTraceEnabled()) {
               log.tracef("%s tryReleaseShared(%s) => SUCCESS", currentRequestor(), System.identityHashCode(this));
            }
            return true;
         }
      }
   }

   @Override
   protected void resetState() {
      super.resetState();
      readCounters.clear();
   }

   private void incrementRead(Object owner) {
      synchronized (readCounters) {
         AtomicInteger counter = readCounters.get(owner);
         if (counter == null) {
            readCounters.put(owner, new AtomicInteger(1));
         } else {
            counter.incrementAndGet();
         }
      }
   }

   private boolean decrementRead(Object owner) {
      synchronized (readCounters) {
         AtomicInteger counter = readCounters.get(owner);
         if (counter == null) {
            return false;
         }
         if (counter.decrementAndGet() == 0) {
            readCounters.remove(owner);
         }
         return true;
      }
   }
}
