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
package org.infinispan.executors;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.Start;
import org.infinispan.factories.annotations.Stop;
import org.infinispan.jmx.annotations.MBean;
import org.infinispan.jmx.annotations.ManagedAttribute;
import org.infinispan.jmx.annotations.ManagedOperation;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.rhq.helpers.pluginAnnotations.agent.DisplayType;
import org.rhq.helpers.pluginAnnotations.agent.Metric;
import org.rhq.helpers.pluginAnnotations.agent.Operation;
import org.rhq.helpers.pluginAnnotations.agent.Units;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
@MBean(objectName = "ConditionalExecutorService", description = "Executor service that put a conditional runnable " +
      "when the runnable is ready to be processed")
public class ConditionalExecutorService {

   private static final Log log = LogFactory.getLog(ConditionalExecutorService.class);
   private volatile SchedulerThread schedulerThread;
   private RunnableEntry activeRunnables;
   private RunnableEntry newRunnables;
   private volatile boolean enabled;
   private Configuration configuration;
   private String cacheName;

   @Inject
   public void inject(Configuration configuration, Cache<?, ?> cache) {
      this.configuration = configuration;
      this.cacheName = cache == null ? "default" : cache.getName();
   }

   @Start
   public final void start() {
      enabled = true;
   }

   @Stop
   public final void stop() {
      enabled = false;
      if (schedulerThread != null) {
         schedulerThread.interrupt();
      }
   }

   public void execute(ConditionalRunnable runnable) throws Exception {
      if (!enabled) {
         throw new Exception("Executor Service is not enabled");
      }
      RunnableEntry runnableEntry = new RunnableEntry(runnable);
      synchronized (this) {
         initIfNeeded();
         runnableEntry.next = newRunnables;
         newRunnables = runnableEntry;
         notify();
         if (log.isTraceEnabled()) {
            log.tracef("Added a new task: %s waiting to be added", toAddSize());
         }
      }
   }

   @ManagedAttribute(description = "The minimum number of threads in the thread pool")
   @Metric(displayName = "Minimum Number of Threads", displayType = DisplayType.DETAIL)
   public int getCorePoolSize() {
      SchedulerThread current = schedulerThread;
      return current == null ? 0 : current.getExecutorService().getCorePoolSize();
   }

   @ManagedOperation(description = "Set the minimum number of threads in the thread pool")
   @Operation(displayName = "Set Minimum Number Of Threads")
   public void setCorePoolSize(int size) {
      SchedulerThread current = schedulerThread;
      if (!enabled || current == null) {
         return;
      }
      current.getExecutorService().setCorePoolSize(size);
   }

   @ManagedAttribute(description = "The maximum number of threads in the thread pool")
   @Metric(displayName = "Maximum Number of Threads", displayType = DisplayType.DETAIL)
   public int getMaximumPoolSize() {
      SchedulerThread current = schedulerThread;
      return current == null ? 0 : current.getExecutorService().getMaximumPoolSize();
   }

   @ManagedOperation(description = "Set the maximum number of threads in the thread pool")
   @Operation(displayName = "Set Maximum Number Of Threads")
   public void setMaximumPoolSize(int size) {
      SchedulerThread current = schedulerThread;
      if (!enabled || current == null) {
         return;
      }
      current.getExecutorService().setMaximumPoolSize(size);
   }

   @ManagedAttribute(description = "The keep alive time of an idle thread in the thread pool (milliseconds)")
   @Metric(displayName = "Keep Alive Time of a Idle Thread", units = Units.MILLISECONDS,
           displayType = DisplayType.DETAIL)
   public long getKeepAliveTime() {
      SchedulerThread current = schedulerThread;
      return current == null ? 0 : current.getExecutorService().getKeepAliveTime(TimeUnit.MILLISECONDS);
   }

   @ManagedOperation(description = "Set the idle time of a thread in the thread pool (milliseconds)")
   @Operation(displayName = "Set Keep Alive Time of Idle Threads")
   public void setKeepAliveTime(long milliseconds) {
      SchedulerThread current = schedulerThread;
      if (!enabled || current == null) {
         return;
      }
      current.getExecutorService().setKeepAliveTime(milliseconds, TimeUnit.MILLISECONDS);
   }

   @ManagedAttribute(description = "The approximate percentage of active threads in the thread pool")
   @Metric(displayName = "Percentage of Active Threads", units = Units.PERCENTAGE, displayType = DisplayType.SUMMARY)
   public double getUsagePercentage() {
      SchedulerThread current = schedulerThread;
      if (current == null) {
         return 0D;
      }

      int max = current.getExecutorService().getMaximumPoolSize();
      int actual = current.getExecutorService().getActiveCount();
      double percentage = actual * 100.0 / max;
      return percentage > 100 ? 100.0 : percentage;
   }

   private ThreadFactory createThreadFactory() {
      return new ThreadFactory() {

         private final AtomicInteger threadCounter = new AtomicInteger(0);

         @SuppressWarnings("NullableProblems")
         @Override
         public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "Cond-ThreadPool-" + cacheName + "-" +
                  threadCounter.incrementAndGet());
         }
      };
   }

   private ThreadPoolExecutor createExecutorService() {
      return new ThreadPoolExecutor(configuration.conditionalExecutorService().corePoolSize(),
                                    configuration.conditionalExecutorService().maxPoolSize(),
                                    configuration.conditionalExecutorService().keepAliveTime(),
                                    TimeUnit.MILLISECONDS,
                                    new SynchronousQueue<Runnable>(),
                                    createThreadFactory(),
                                    new ThreadPoolExecutor.CallerRunsPolicy());
   }

   private int count(RunnableEntry first) {
      int size = 0;
      RunnableEntry iterator = first;
      while (iterator != null) {
         size++;
         iterator = iterator.next;
      }
      return size;
   }

   private synchronized void clearAll() {
      newRunnables = null;
      activeRunnables = null;
   }

   private void addNewRunnables() throws InterruptedException {
      RunnableEntry addList;
      synchronized (this) {
         addList = newRunnables;
         newRunnables = null;
         if (addList == null && activeRunnables == null) {
            wait();
         }
      }

      if (addList == null) {
         return;
      } else if (activeRunnables == null) {
         activeRunnables = addList;
         if (log.isTraceEnabled()) {
            log.tracef("Adding pending tasks. Active=%s", count(activeRunnables));
         }
         return;
      }

      RunnableEntry last = activeRunnables;
      while (last.next != null) {
         last = last.next;
      }
      last.next = addList;
      if (log.isTraceEnabled()) {
         log.tracef("Adding pending tasks. Active=%s", count(activeRunnables));
      }
   }

   private synchronized int toAddSize() {
      return count(newRunnables);
   }

   private void initIfNeeded() {
      if (!enabled || schedulerThread != null) {
         return;
      }
      schedulerThread = new SchedulerThread(cacheName);
      schedulerThread.start();
   }

   private class SchedulerThread extends Thread {

      private final ThreadPoolExecutor executorService;
      private volatile boolean running;

      public SchedulerThread(String cacheName) {
         super("Scheduler-" + cacheName);
         this.executorService = createExecutorService();
      }

      @Override
      public void run() {
         running = true;
         while (running) {
            try {
               addNewRunnables();

               if (activeRunnables == null) {
                  continue;
               }

               int tasksExecuted = 0;

               while (activeRunnables != null && activeRunnables.runnable.isReady()) {
                  executorService.execute(activeRunnables.runnable);
                  activeRunnables = activeRunnables.next;
                  tasksExecuted++;
               }

               if (activeRunnables == null) {
                  if (log.isTraceEnabled() && tasksExecuted > 0) {
                     log.tracef("Tasks executed=%s, still active=%s", tasksExecuted, count(activeRunnables));
                  }
                  continue;
               }

               RunnableEntry iterator = activeRunnables;
               while (iterator.next != null) {
                  RunnableEntry toAnalyze = iterator.next;
                  if (toAnalyze.runnable.isReady()) {
                     executorService.execute(toAnalyze.runnable);
                     iterator.next = toAnalyze.next;
                     tasksExecuted++;
                  } else {
                     iterator = iterator.next;
                  }
               }

               if (log.isTraceEnabled() && tasksExecuted > 0) {
                  log.tracef("Tasks executed=%s, still active=%s", tasksExecuted, count(activeRunnables));
               }
            } catch (InterruptedException e) {
               break;
            } catch (Throwable throwable) {
               if (log.isTraceEnabled()) {
                  log.tracef(throwable, "Exception caught while executing task");
               } else {
                  log.warnf("Exception caught while executing task: %s", throwable.getLocalizedMessage());
               }

            }
         }
         executorService.shutdown();
         clearAll();
      }

      @Override
      public void interrupt() {
         running = false;
         super.interrupt();
      }

      public ThreadPoolExecutor getExecutorService() {
         return executorService;
      }
   }

   private class RunnableEntry {
      private final ConditionalRunnable runnable;
      private RunnableEntry next;

      private RunnableEntry(ConditionalRunnable runnable) {
         this.runnable = runnable;
         this.next = null;
      }
   }
}
