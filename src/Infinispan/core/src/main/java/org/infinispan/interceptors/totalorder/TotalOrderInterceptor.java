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
package org.infinispan.interceptors.totalorder;

import org.infinispan.commands.control.LockControlCommand;
import org.infinispan.commands.tx.CommitCommand;
import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.tx.RollbackCommand;
import org.infinispan.commands.tx.VersionedCommitCommand;
import org.infinispan.container.versioning.EntryVersionsMap;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.Start;
import org.infinispan.interceptors.base.CommandInterceptor;
import org.infinispan.transaction.LocalTransaction;
import org.infinispan.transaction.RemoteTransaction;
import org.infinispan.transaction.totalorder.TotalOrderManager;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * Created to control the total order validation. It disable the possibility of acquiring locks during execution through
 * the cache API
 *
 * @author Pedro Ruivo
 * @author Mircea.Markus@jboss.com
 * @since 5.2
 */
public class TotalOrderInterceptor extends CommandInterceptor {

   private static final Log log = LogFactory.getLog(TotalOrderInterceptor.class);
   private boolean trace;

   private TotalOrderManager totalOrderManager;

   @Inject
   public void inject(TotalOrderManager totalOrderManager) {
      this.totalOrderManager = totalOrderManager;
   }

   @Start
   public void setLogLevel() {
      this.trace = log.isTraceEnabled();
   }

   @Override
   public Object visitPrepareCommand(TxInvocationContext ctx, PrepareCommand command) throws Throwable {
      if (trace) {
         log.tracef("Visit Prepare Command. Transaction is %s, Affected keys are %s, Should invoke remotely? %s",
                    command.getGlobalTransaction().prettyPrint(),
                    command.getAffectedKeys(),
                    ctx.hasModifications());
      }

      try {
         if (ctx.isOriginLocal()) {
            totalOrderManager.addLocalTransaction(command.getGlobalTransaction(),
                                                  (LocalTransaction) ctx.getCacheTransaction());
            return invokeNextInterceptor(ctx, command);
         } else {
            return totalOrderManager.processTransactionFromSequencer(command, ctx, getNext());
         }
      } catch (Throwable t) {
         if (trace) {
            log.tracef("Exception caught while visiting prepare command. Transaction is %s, Local? %s, " +
                             "version seen are %s, error message is %s",
                       command.getGlobalTransaction().prettyPrint(),
                       ctx.isOriginLocal(), ctx.getCacheTransaction().getUpdatedEntryVersions(),
                       t.getMessage());
         }
         throw t;
      }
   }

   @Override
   public Object visitLockControlCommand(TxInvocationContext ctx, LockControlCommand command) throws Throwable {
      throw new UnsupportedOperationException("Lock interface not supported with total order protocol");
   }

   //The rollback and commit command are only invoked with repeatable read + write skew + versioning
   @Override
   public Object visitRollbackCommand(TxInvocationContext ctx, RollbackCommand command) throws Throwable {
      GlobalTransaction gtx = command.getGlobalTransaction();
      if (trace)  log.tracef("Visit Rollback Command. Transaction is %s", gtx.prettyPrint());

      boolean processCommand = true;
      RemoteTransaction remoteTransaction = null;

      try {
         if (!ctx.isOriginLocal()) {
            remoteTransaction = (RemoteTransaction) ctx.getCacheTransaction();
            processCommand = totalOrderManager.waitForTxPrepared(remoteTransaction, false, null);
            if (!processCommand) {
               return null;
            }
         }

         return invokeNextInterceptor(ctx, command);
      } catch (Throwable t) {
         if (trace) {
            log.tracef("Exception caught while visiting local rollback command. Transaction is %s, " +
                             "error message is %s",
                       gtx.prettyPrint(), t.getMessage());
         }
         throw t;
      } finally {
         if (processCommand) {
            totalOrderManager.finishTransaction(gtx, !ctx.isOriginLocal() || !ctx.getCacheTransaction().wasPrepareSent(),
                                                remoteTransaction);
         }
      }
   }

   @Override
   public Object visitCommitCommand(TxInvocationContext ctx, CommitCommand command) throws Throwable {
      GlobalTransaction gtx = command.getGlobalTransaction();

      if (trace) log.tracef("Visit Commit Command. Transaction is %s", gtx.prettyPrint());

      boolean processCommand = true;
      RemoteTransaction remoteTransaction = null;

      try {
         if (!ctx.isOriginLocal()) {
            EntryVersionsMap newVersions =
                  command instanceof VersionedCommitCommand ? ((VersionedCommitCommand) command).getUpdatedVersions() : null;
            remoteTransaction = (RemoteTransaction) ctx.getCacheTransaction();
            processCommand = totalOrderManager.waitForTxPrepared(remoteTransaction, true, newVersions);
            if (!processCommand) {
               return null;
            }
         }

         return invokeNextInterceptor(ctx, command);
      } catch (Throwable t) {
         if (trace) {
            log.tracef("Exception caught while visiting local commit command. Transaction is %s, " +
                             "version seen are %s, error message is %s",
                       gtx.prettyPrint(),
                       ctx.getCacheTransaction().getUpdatedEntryVersions(), t.getMessage());
         }
         throw t;
      } finally {
         if (processCommand) {
            totalOrderManager.finishTransaction(gtx, !ctx.hasModifications(), remoteTransaction);
         }
      }
   }
}
