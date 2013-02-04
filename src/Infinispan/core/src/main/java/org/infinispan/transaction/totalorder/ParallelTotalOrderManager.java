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
package org.infinispan.transaction.totalorder;

import org.infinispan.CacheException;
import org.infinispan.commands.tx.GMUPrepareCommand;
import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.context.Flag;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.executors.ConditionalExecutorService;
import org.infinispan.executors.ConditionalRunnable;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.interceptors.base.CommandInterceptor;
import org.infinispan.jmx.annotations.MBean;
import org.infinispan.jmx.annotations.ManagedAttribute;
import org.infinispan.jmx.annotations.ManagedOperation;
import org.infinispan.transaction.RemoteTransaction;
import org.infinispan.transaction.TxDependencyLatch;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.jgroups.blocks.RequestHandler;
import org.rhq.helpers.pluginAnnotations.agent.DisplayType;
import org.rhq.helpers.pluginAnnotations.agent.Metric;
import org.rhq.helpers.pluginAnnotations.agent.Units;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Pedro Ruivo
 * @author Mircea.markus@jboss.org
 * @since 5.2
 */
@MBean(objectName = "ParallelTotalOrderManager", description = "Total order Manager used when the transactions are " +
      "committed in two phases")
public class ParallelTotalOrderManager extends BaseTotalOrderManager {

   private static final Log log = LogFactory.getLog(ParallelTotalOrderManager.class);
   private final AtomicLong waitTimeInQueue = new AtomicLong(0);
   private final AtomicLong initializationDuration = new AtomicLong(0);
   /**
    * this map is used to keep track of concurrent transactions.
    */
   private final ConcurrentMap<Object, TxDependencyLatch> keysLocked = new ConcurrentHashMap<Object, TxDependencyLatch>();
   private ConditionalExecutorService validationExecutorService;

   @Inject
   public void inject(ConditionalExecutorService conditionalExecutorService) {
      validationExecutorService = conditionalExecutorService;
   }

   @Override
   public final Object processTransactionFromSequencer(PrepareCommand prepareCommand, TxInvocationContext ctx,
                                                       CommandInterceptor invoker) {
      logAndCheckContext(prepareCommand, ctx);

      copyLookedUpEntriesToRemoteContext(ctx);

      RemoteTransaction remoteTransaction = (RemoteTransaction) ctx.getCacheTransaction();

      ParallelPrepareProcessor ppp = constructParallelPrepareProcessor(prepareCommand, ctx, invoker, remoteTransaction);
      Set<TxDependencyLatch> previousTxs = new HashSet<TxDependencyLatch>();
      Set<Object> keysModified = getModifiedKeyFromModifications(remoteTransaction.getModifications());

      //this will collect all the count down latch corresponding to the previous transactions in the queue
      for (Object key : keysModified) {
         TxDependencyLatch prevTx = keysLocked.put(key, remoteTransaction.getDependencyLatch());
         if (prevTx != null) {
            previousTxs.add(prevTx);
         }
      }

      if (prepareCommand instanceof GMUPrepareCommand) {
         for (Object key : ((GMUPrepareCommand) prepareCommand).getReadSet()) {
            previousTxs.add(keysLocked.get(key));
         }
         previousTxs.remove(remoteTransaction.getDependencyLatch());
      }

      ppp.setPreviousTransactions(previousTxs);

      if (trace)
         log.tracef("Transaction [%s] write set is %s", remoteTransaction.getDependencyLatch(), keysModified);

      try {
         validationExecutorService.execute(ppp);
      } catch (Exception e) {
         log.fatal("Executor service is not enabled!");
         throw new RuntimeException(e);
      }
      return RequestHandler.DO_NOT_REPLY;
   }

   @Override
   public final void finishTransaction(RemoteTransaction remoteTransaction) {
      super.finishTransaction(remoteTransaction);
      for (Object key : getModifiedKeyFromModifications(remoteTransaction.getModifications())) {
         this.keysLocked.remove(key, remoteTransaction.getDependencyLatch());
      }
   }

   @Override
   public Set<TxDependencyLatch> getPendingCommittingTransaction() {
      return new HashSet<TxDependencyLatch>(keysLocked.values());
   }

   @ManagedOperation(description = "Resets the statistics")
   public void resetStatistics() {
      super.resetStatistics();
      waitTimeInQueue.set(0);
      initializationDuration.set(0);
   }

   @ManagedAttribute(description = "Average time in the queue before the validation (milliseconds)")
   @Metric(displayName = "Average Waiting Duration In Queue", units = Units.MILLISECONDS,
           displayType = DisplayType.SUMMARY)
   public double getAverageWaitingTimeInQueue() {
      long time = waitTimeInQueue.get();
      int tx = numberOfTxValidated.get();
      if (tx == 0) {
         return 0;
      }
      return (time / tx) / 1000000.0;
   }

   @ManagedAttribute(description = "Average duration of a transaction initialization before validation, ie, " +
         "ensuring the order of transactions (milliseconds)")
   @Metric(displayName = "Average Initialization Duration", units = Units.MILLISECONDS,
           displayType = DisplayType.SUMMARY)
   public double getAverageInitializationDuration() {
      long time = initializationDuration.get();
      int tx = numberOfTxValidated.get();
      if (tx == 0) {
         return 0;
      }
      return (time / tx) / 1000000.0;
   }

   /**
    * constructs a new thread to be passed to the thread pool. this is overridden in distributed mode that has a
    * different behavior
    *
    * @param prepareCommand      the prepare command
    * @param txInvocationContext the context
    * @param invoker             the next interceptor
    * @param remoteTransaction   the remote transaction
    * @return a new thread
    */
   private ParallelPrepareProcessor constructParallelPrepareProcessor(PrepareCommand prepareCommand, TxInvocationContext txInvocationContext,
                                                                      CommandInterceptor invoker, RemoteTransaction remoteTransaction) {
      return new ParallelPrepareProcessor(prepareCommand, txInvocationContext, invoker, remoteTransaction);
   }

   /**
    * updates the accumulating time for profiling information
    *
    * @param creationTime          the arrival timestamp of the prepare command to this component in remote
    * @param validationStartTime   the processing start timestamp
    * @param validationEndTime     the validation ending timestamp
    * @param initializationEndTime the initialization ending timestamp
    */
   private void updateDurationStats(long creationTime, long validationStartTime, long validationEndTime,
                                    long initializationEndTime) {
      if (statisticsEnabled) {
         //set the profiling information
         waitTimeInQueue.addAndGet(validationStartTime - creationTime);
         initializationDuration.addAndGet(initializationEndTime - validationStartTime);
         processingDuration.addAndGet(validationEndTime - initializationEndTime);
         numberOfTxValidated.incrementAndGet();
      }
   }

   /**
    * This class is used to validate transaction in repeatable read with write skew check
    */
   private class ParallelPrepareProcessor implements ConditionalRunnable {

      protected final RemoteTransaction remoteTransaction;
      protected final PrepareCommand prepareCommand;
      //the set of others transaction's count down latch (it will be unblocked when the transaction finishes)
      private final Set<TxDependencyLatch> previousTransactions;
      private final TxInvocationContext txInvocationContext;
      private final CommandInterceptor invoker;
      private long creationTime = -1;
      private long processStartTime = -1;
      private long initializationEndTime = -1;

      protected ParallelPrepareProcessor(PrepareCommand prepareCommand, TxInvocationContext txInvocationContext,
                                         CommandInterceptor invoker, RemoteTransaction remoteTransaction) {
         if (prepareCommand == null || txInvocationContext == null || invoker == null) {
            throw new IllegalArgumentException("Arguments must not be null");
         }
         this.prepareCommand = prepareCommand;
         this.txInvocationContext = txInvocationContext;
         this.invoker = invoker;
         this.creationTime = now();
         this.previousTransactions = new HashSet<TxDependencyLatch>();
         this.remoteTransaction = remoteTransaction;
      }

      public void setPreviousTransactions(Set<TxDependencyLatch> previousTransactions) {
         this.previousTransactions.addAll(previousTransactions);
      }

      @Override
      public final void run() {
         processStartTime = now();
         boolean exception = false;
         try {
            if (trace) log.tracef("Validating transaction %s ",
                                  prepareCommand.getGlobalTransaction().prettyPrint());
            initializeValidation();
            initializationEndTime = now();

            //invoke next interceptor in the chain
            Object result = prepareCommand.acceptVisitor(txInvocationContext, invoker);
            prepareCommand.sendReply(result, false);
         } catch (Throwable t) {
            log.trace("Exception while processing the rest of the interceptor chain", t);
            if (initializationEndTime == -1) {
               initializationEndTime = now();
            }
            prepareCommand.sendReply(t, true);
            exception = true;
         } finally {
            logProcessingFinalStatus(prepareCommand, exception);
            finishPrepare(exception);
            updateDurationStats(creationTime, processStartTime, now(), initializationEndTime);
         }
      }

      @Override
      public final boolean isReady() {
         previousTransactions.remove(remoteTransaction.getDependencyLatch());
         for (TxDependencyLatch prevTx : previousTransactions) {
            try {
               if (!prevTx.await(0, TimeUnit.SECONDS)) {
                  if (log.isTraceEnabled()) {
                     log.tracef("[%s] is not ready to prepare due to %s",
                                prepareCommand.getGlobalTransaction().prettyPrint(), prevTx);
                  }
                  return false;
               }
            } catch (InterruptedException e) {
               if (log.isTraceEnabled()) {
                  log.tracef("[%s ]Interrupted while checking is the previous conflicting transactions has finished",
                             prepareCommand.getGlobalTransaction().prettyPrint());
               }
               return false;
            }
         }
         if (log.isTraceEnabled()) {
            log.tracef("[%s] is ready to prepare", prepareCommand.getGlobalTransaction().prettyPrint());
         }
         return true;
      }

      /**
       * set the initialization of the thread before the validation ensures the validation order in conflicting
       * transactions
       *
       * @throws InterruptedException if this thread was interrupted
       */
      protected void initializeValidation() throws Exception {
         String gtx = prepareCommand.getGlobalTransaction().prettyPrint();
         //TODO is this really needed?
         invocationContextContainer.setContext(txInvocationContext);
         remoteTransaction.markForPreparing();

         if (remoteTransaction.isMarkedForRollback()) {
            //this means that rollback has already been received
            transactionTable.removeRemoteTransaction(remoteTransaction.getGlobalTransaction());
            throw new CacheException("Cannot prepare transaction" + gtx + ". it was already marked as rollback");
         }

         if (remoteTransaction.isMarkedForCommit()) {
            log.tracef("Transaction %s marked for commit, skipping the write skew check and forcing 1PC", gtx);
            txInvocationContext.setFlags(Flag.SKIP_WRITE_SKEW_CHECK);
            prepareCommand.setOnePhaseCommit(true);
         }
      }

      /**
       * finishes the transaction, ie, mark the modification as applied and set the result (exception or not) invokes
       * the method {@link this.finishTransaction} if the transaction has the one phase commit set to true
       *
       * @param exception true if the result is an exception
       */
      private void finishPrepare(boolean exception) {
         remoteTransaction.markPreparedAndNotify();
         if (prepareCommand.isOnePhaseCommit()) {
            finishTransaction(remoteTransaction);
            transactionTable.removeRemoteTransaction(prepareCommand.getGlobalTransaction());
         } else if (exception) {
            finishTransaction(remoteTransaction);
            //Note: I cannot remove from the remote table, otherwise, when the rollback arrives, it will create a
            // new remote transaction!
         }
      }
   }
}
