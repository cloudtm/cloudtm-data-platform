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
