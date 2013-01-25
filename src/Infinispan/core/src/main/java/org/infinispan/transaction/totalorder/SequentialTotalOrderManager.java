package org.infinispan.transaction.totalorder;

import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.interceptors.base.CommandInterceptor;
import org.infinispan.jmx.annotations.MBean;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * @author mircea.markus@jboss.com
 * @author Pedro Ruivo
 * @since 5.2.0
 */
@MBean(objectName = "SequentialTotalOrderManager", description = "Total order Manager used when the transaction are " +
      "committed in one phase")
public class SequentialTotalOrderManager extends BaseTotalOrderManager {

   private static final Log log = LogFactory.getLog(SequentialTotalOrderManager.class);

   public final Object processTransactionFromSequencer(PrepareCommand prepareCommand, TxInvocationContext ctx,
                                                       CommandInterceptor invoker) throws Throwable {

      logAndCheckContext(prepareCommand, ctx);

      copyLookedUpEntriesToRemoteContext(ctx);

      boolean exception = false;
      long startTime = now();
      try {
         return prepareCommand.acceptVisitor(ctx, invoker);
      } catch (Throwable t) {
         log.trace("Exception while processing the rest of the interceptor chain", t);
         //if an exception is throw, the TxInterceptor will not remove it from the TxTable and the rollback is not
         //sent (with TO)
         transactionTable.remoteTransactionRollback(prepareCommand.getGlobalTransaction());
         exception = true;
         throw t;
      } finally {
         logProcessingFinalStatus(prepareCommand, exception);
         updateProcessingDurationStats(startTime, now());
      }
   }

   private void updateProcessingDurationStats(long start, long end) {
      if (statisticsEnabled) {
         processingDuration.addAndGet(end - start);
         numberOfTxValidated.incrementAndGet();
      }
   }
}
