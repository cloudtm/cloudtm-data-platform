package org.infinispan.interceptors.totalorder;

import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.interceptors.ReplicationInterceptor;

import static org.infinispan.interceptors.totalorder.TotalOrderHelper.*;

/**
 * @author mircea.markus@jboss.com
 * @since 5.2.0
 */
public class TotalOrderReplicationInterceptor extends ReplicationInterceptor implements TotalOrderRpcInterceptor {

   @Override
   public final Object visitPrepareCommand(TxInvocationContext ctx, PrepareCommand command) throws Throwable {
      return prepare(ctx, command, this);
   }

   @Override
   public Object visitPrepare(TxInvocationContext context, PrepareCommand command) throws Throwable {
      return super.visitPrepareCommand(context, command);
   }

   @Override
   protected void broadcastPrepare(TxInvocationContext context, PrepareCommand command) {
      boolean waitOnlySelfDeliver =!configuration.isSyncCommitPhase();
      totalOrderBroadcastPrepare(command, null, null, rpcManager, waitOnlySelfDeliver,
                                 configuration.isSyncCommitPhase(), configuration.getSyncReplTimeout());
   }
}
