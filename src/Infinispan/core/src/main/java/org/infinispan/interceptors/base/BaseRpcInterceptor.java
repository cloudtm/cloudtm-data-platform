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
package org.infinispan.interceptors.base;

import org.infinispan.commands.control.LockControlCommand;
import org.infinispan.commands.tx.RollbackCommand;
import org.infinispan.context.Flag;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.impl.LocalTxInvocationContext;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.Start;
import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.xa.CacheTransaction;

/**
 * Acts as a base for all RPC calls
 *
 * @author <a href="mailto:manik@jboss.org">Manik Surtani (manik@jboss.org)</a>
 * @author Mircea.Markus@jboss.com
 * @since 4.0
 */
public abstract class BaseRpcInterceptor extends CommandInterceptor {

   protected RpcManager rpcManager;

   @Inject
   public void init(RpcManager rpcManager) {
      this.rpcManager = rpcManager;
   }

   protected boolean defaultSynchronous;
   protected long timeout;

   @Start
   public void init() {
      defaultSynchronous = configuration.getCacheMode().isSynchronous();
      timeout = configuration.getSyncReplTimeout();
   }

   @Override
   public Object visitLockControlCommand(TxInvocationContext ctx, LockControlCommand command) throws Throwable {
      Object retVal = invokeNextInterceptor(ctx, command);
      if (ctx.isOriginLocal()) {
         //unlock will happen async as it is a best effort
         boolean sync = !command.isUnlock();
         command.setFlags(ctx.getFlags());
         ((LocalTxInvocationContext) ctx).remoteLocksAcquired(rpcManager.getTransport().getMembers());
         rpcManager.broadcastRpcCommand(command, sync, false, false);
      }
      return retVal;
   }

   protected final boolean isSynchronous(InvocationContext ctx) {
      if (ctx.hasFlag(Flag.FORCE_SYNCHRONOUS))
         return true;
      else if (ctx.hasFlag(Flag.FORCE_ASYNCHRONOUS))
         return false;

      return defaultSynchronous;
   }

   protected final boolean isLocalModeForced(InvocationContext ctx) {
      if (ctx.hasFlag(Flag.CACHE_MODE_LOCAL)) {
         if (getLog().isTraceEnabled()) getLog().trace("LOCAL mode forced on invocation.  Suppressing clustered events.");
         return true;
      }
      return false;
   }

   protected static boolean shouldInvokeRemoteTxCommand(TxInvocationContext ctx) {
      // just testing for empty modifications isn't enough - the Lock API may acquire locks on keys but won't
      // register a Modification.  See ISPN-711.
      return ctx.isOriginLocal() && (ctx.hasModifications()  ||
                                           !((LocalTxInvocationContext) ctx).getRemoteLocksAcquired().isEmpty());
   }

   /**
    * check if the rollback command should be sent remotely or not.
    *
    * Rules:
    *  1) if prepare was sent, then the rollback should be sent
    *  2) if prepare was not sent then we have two cases:
    *    a) in total order, no locks are acquired during execution, so we can avoid the invoke remotely
    *    b) in pessimist locking, lock *can* be acquired and then the command should be sent
    *
    * @param ctx     the invocation context
    * @param command the rollback command
    * @return        true if it should be invoked, false otherwise
    */
   protected final boolean shouldInvokeRemoteRollbackCommand(TxInvocationContext ctx, RollbackCommand command) {
      CacheTransaction cacheTransaction = ctx.getCacheTransaction();
      command.setPrepareSent(cacheTransaction.wasPrepareSent());
      boolean totalOrder = command.getGlobalTransaction().getReconfigurableProtocol().useTotalOrder();
      return cacheTransaction.wasPrepareSent() ||
            (!totalOrder && configuration.getTransactionLockingMode() == LockingMode.PESSIMISTIC);
   }
}