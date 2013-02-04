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
package org.infinispan.reconfigurableprotocol.component;

import org.infinispan.commands.VisitableCommand;
import org.infinispan.commands.control.LockControlCommand;
import org.infinispan.commands.tx.CommitCommand;
import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.tx.RollbackCommand;
import org.infinispan.commands.write.WriteCommand;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.statetransfer.StateTransferLock;
import org.infinispan.statetransfer.StateTransferLockReacquisitionException;

import java.util.concurrent.TimeoutException;

/**
 * Delegates the method invocations for the correct instance depending of the protocol, for the StateTransferLock
 * component
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class StateTransferLockDelegate extends AbstractProtocolDependentComponent<StateTransferLock>
      implements StateTransferLock {

   @Override
   public boolean acquireForCommand(InvocationContext ctx, WriteCommand command) throws InterruptedException, TimeoutException {
      return get().acquireForCommand(ctx, command);
   }

   @Override
   public boolean acquireForCommand(TxInvocationContext ctx, PrepareCommand command) throws InterruptedException, TimeoutException {
      return get().acquireForCommand(ctx, command);
   }

   @Override
   public boolean acquireForCommand(TxInvocationContext ctx, CommitCommand command) throws InterruptedException, TimeoutException {
      return get().acquireForCommand(ctx, command);
   }

   @Override
   public boolean acquireForCommand(TxInvocationContext ctx, RollbackCommand command) throws InterruptedException, TimeoutException {
      return get().acquireForCommand(ctx, command);
   }

   @Override
   public boolean acquireForCommand(TxInvocationContext ctx, LockControlCommand cmd) throws TimeoutException, InterruptedException {
      return get().acquireForCommand(ctx, cmd);
   }

   @Override
   public void releaseForCommand(InvocationContext ctx, WriteCommand command) {
      get().releaseForCommand(ctx, command);
   }

   @Override
   public void releaseForCommand(TxInvocationContext ctx, PrepareCommand command) {
      get().releaseForCommand(ctx, command);
   }

   @Override
   public void releaseForCommand(TxInvocationContext ctx, CommitCommand command) {
      get().releaseForCommand(ctx, command);
   }

   @Override
   public void releaseForCommand(TxInvocationContext ctx, RollbackCommand command) {
      get().releaseForCommand(ctx, command);
   }

   @Override
   public void releaseForCommand(TxInvocationContext ctx, LockControlCommand cmd) {
      get().releaseForCommand(ctx, cmd);
   }

   @Override
   public void blockNewTransactions(int cacheViewId) throws InterruptedException {
      get().blockNewTransactions(cacheViewId);
   }

   @Override
   public void unblockNewTransactions(int cacheViewId) {
      get().unblockNewTransactions(cacheViewId);
   }

   @Override
   public void blockNewTransactionsAsync() {
      get().blockNewTransactionsAsync();
   }

   @Override
   public boolean areNewTransactionsBlocked() {
      return get().areNewTransactionsBlocked();
   }

   @Override
   public int getBlockingCacheViewId() {
      return get().getBlockingCacheViewId();
   }

   @Override
   public void waitForStateTransferToEnd(InvocationContext ctx, VisitableCommand command, int newCacheViewId) throws TimeoutException, InterruptedException, StateTransferLockReacquisitionException {
      get().waitForStateTransferToEnd(ctx, command, newCacheViewId);
   }
}
