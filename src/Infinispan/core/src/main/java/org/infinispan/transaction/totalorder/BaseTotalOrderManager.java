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

import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.write.WriteCommand;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.container.DataContainer;
import org.infinispan.container.versioning.EntryVersionsMap;
import org.infinispan.context.InvocationContextContainer;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.Start;
import org.infinispan.interceptors.locking.ClusteringDependentLogic;
import org.infinispan.jmx.annotations.ManagedAttribute;
import org.infinispan.jmx.annotations.ManagedOperation;
import org.infinispan.transaction.LocalTransaction;
import org.infinispan.transaction.RemoteTransaction;
import org.infinispan.transaction.TransactionTable;
import org.infinispan.transaction.TxDependencyLatch;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.util.Util;
import org.infinispan.util.concurrent.ConcurrentMapFactory;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.rhq.helpers.pluginAnnotations.agent.DisplayType;
import org.rhq.helpers.pluginAnnotations.agent.Metric;
import org.rhq.helpers.pluginAnnotations.agent.Units;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author mircea.markus@jboss.com
 * @author Pedro Ruivo
 * @since 5.2
 */
public abstract class BaseTotalOrderManager implements TotalOrderManager {

   private static final Log log = LogFactory.getLog(BaseTotalOrderManager.class);
   protected static boolean trace;
   protected final AtomicLong processingDuration = new AtomicLong(0);
   protected final AtomicInteger numberOfTxValidated = new AtomicInteger(0);
   /**
    * Map between GlobalTransaction and LocalTransaction. Used to sync the threads in remote validation and the
    * transaction execution thread.
    */
   private final ConcurrentMap<GlobalTransaction, LocalTransaction> localTransactionMap =
         ConcurrentMapFactory.makeConcurrentMap();
   protected Configuration configuration;
   protected InvocationContextContainer invocationContextContainer;
   protected TransactionTable transactionTable;
   protected DataContainer dataContainer;
   /**
    * Volatile as its value can be changed by a JMX thread.
    */
   protected volatile boolean statisticsEnabled;
   private ClusteringDependentLogic clusteringDependentLogic;

   @Inject
   public void inject(Configuration configuration, InvocationContextContainer invocationContextContainer,
                      TransactionTable transactionTable, DataContainer dataContainer, ClusteringDependentLogic clusteringDependentLogic) {
      this.configuration = configuration;
      this.invocationContextContainer = invocationContextContainer;
      this.transactionTable = transactionTable;
      this.dataContainer = dataContainer;
      this.clusteringDependentLogic = clusteringDependentLogic;
   }

   @Start
   public void start() {
      trace = log.isTraceEnabled();
      setStatisticsEnabled(configuration.jmxStatistics().enabled());
   }

   @Override
   public void addLocalTransaction(GlobalTransaction globalTransaction, LocalTransaction localTransaction) {
      localTransactionMap.put(globalTransaction, localTransaction);
   }

   @Override
   public final void finishTransaction(GlobalTransaction gtx, boolean ignoreNullTxInfo, RemoteTransaction transaction) {
      if (trace) log.tracef("transaction %s is finished", gtx.prettyPrint());

      RemoteTransaction remoteTransaction = transactionTable.removeRemoteTransaction(gtx);

      if (remoteTransaction == null) {
         remoteTransaction = transaction;
      }

      if (remoteTransaction != null) {
         finishTransaction(remoteTransaction);
      } else if (!ignoreNullTxInfo) {
         log.remoteTransactionIsNull(gtx.prettyPrint());
      }
   }

   @Override
   public final boolean waitForTxPrepared(RemoteTransaction remoteTransaction, boolean commit,
                                          EntryVersionsMap newVersions) {
      GlobalTransaction gtx = remoteTransaction.getGlobalTransaction();
      if (trace)
         log.tracef("%s command received. Waiting until transaction %s is prepared. New versions are %s",
                    commit ? "Commit" : "Rollback", gtx.prettyPrint(), newVersions);

      boolean needsToProcessCommand;
      try {
         needsToProcessCommand = remoteTransaction.waitPrepared(commit, newVersions);
         if (trace) log.tracef("Transaction %s successfully finishes the waiting time until prepared. " +
                                     "%s command will be processed? %s", gtx.prettyPrint(),
                               commit ? "Commit" : "Rollback", needsToProcessCommand ? "yes" : "no");
      } catch (InterruptedException e) {
         log.timeoutWaitingUntilTransactionPrepared(gtx.prettyPrint());
         needsToProcessCommand = false;
      }
      return needsToProcessCommand;
   }

   @ManagedAttribute(description = "Average duration of a transaction validation (milliseconds)")
   @Metric(displayName = "Average Validation Duration", units = Units.MILLISECONDS, displayType = DisplayType.SUMMARY)
   public double getAverageValidationDuration() {
      long time = processingDuration.get();
      int tx = numberOfTxValidated.get();
      if (tx == 0) {
         return 0;
      }
      return (time / tx) / 1000000.0;
   }

   @ManagedOperation(description = "Resets the statistics")
   public void resetStatistics() {
      processingDuration.set(0);
      numberOfTxValidated.set(0);
   }

   @ManagedAttribute(description = "Show it the gathering of statistics is enabled")
   public boolean isStatisticsEnabled() {
      return statisticsEnabled;
   }

   @ManagedOperation(description = "Enables or disables the gathering of statistics by this component")
   public void setStatisticsEnabled(boolean statisticsEnabled) {
      this.statisticsEnabled = statisticsEnabled;
   }

   @Override
   public Set<TxDependencyLatch> getPendingCommittingTransaction() {
      return Collections.emptySet();
   }

   @Override
   public boolean isCoordinatedLocally(GlobalTransaction globalTransaction) {
      return localTransactionMap.containsKey(globalTransaction);
   }

   /**
    * Remove the keys from the map (if their didn't change) and release the count down latch, unblocking the next
    * transaction
    * @param remoteTransaction the remote transaction
    */
   protected void finishTransaction(RemoteTransaction remoteTransaction) {
      TxDependencyLatch latch = remoteTransaction.getDependencyLatch();
      if (trace) log.tracef("Releasing resources for transaction %s", remoteTransaction);
      latch.countDown();
   }

   protected final long now() {
      //we know that this is only used for stats
      return statisticsEnabled ? System.nanoTime() : -1;
   }

   protected final void copyLookedUpEntriesToRemoteContext(TxInvocationContext ctx) {
      LocalTransaction localTransaction = localTransactionMap.get(ctx.getGlobalTransaction());
      if (localTransaction != null) {
         ctx.putLookedUpEntries(localTransaction.getLookedUpEntries());
      }
   }

   protected final void logAndCheckContext(PrepareCommand prepareCommand, TxInvocationContext ctx) {
      if (trace) log.tracef("Processing transaction from sequencer: %s", prepareCommand.getGlobalTransaction().prettyPrint());

      if (ctx.isOriginLocal()) throw new IllegalArgumentException("Local invocation not allowed!");
   }

   protected final void logProcessingFinalStatus(PrepareCommand prepareCommand, boolean exception) {
      if (trace)
         log.tracef("[%s] finished prepare ==> %s", prepareCommand.getGlobalTransaction().prettyPrint(),
                    (exception ? "FAILED" : "OK"));
   }

   /**
    * calculates the keys affected by the list of modification. This method should return only the key own by this node
    * @param modifications the list of modifications
    * @return a set of local keys
    */
   protected final Set<Object> getModifiedKeyFromModifications(Collection<WriteCommand> modifications) {
      if (modifications == null) {
         return Collections.emptySet();
      }
      return filterLocalKeys(Util.getAffectedKeys(modifications, dataContainer));
   }

   private Set<Object> filterLocalKeys(Set<Object> keys) {
      if (keys == null || keys.isEmpty()) {
         return keys;
      }
      Iterator<Object> iterator = keys.iterator();
      while (iterator.hasNext()) {
         Object key = iterator.next();
         if (!clusteringDependentLogic.localNodeIsOwner(key)) {
            iterator.remove();
         }
      }
      return keys;
   }
}
