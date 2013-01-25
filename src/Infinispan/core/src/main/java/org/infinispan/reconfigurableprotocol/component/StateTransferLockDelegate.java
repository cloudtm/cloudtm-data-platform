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
