package org.infinispan.interceptors.gmu;

import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.remoting.responses.Response;

import java.util.Collection;

import static org.infinispan.interceptors.totalorder.TotalOrderHelper.*;
import static org.infinispan.transaction.gmu.GMUHelper.joinAndSetTransactionVersion;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class TotalOrderGMUReplicationInterceptor extends GMUReplicationInterceptor implements TotalOrderRpcInterceptor {

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
      Collection<Response> responses = totalOrderBroadcastPrepare(command, null, null, rpcManager, waitOnlySelfDeliver,
                                                                  configuration.isSyncCommitPhase(),
                                                                  configuration.getSyncReplTimeout());
      joinAndSetTransactionVersion(responses, context, versionGenerator);
   }
}
