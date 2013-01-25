package org.infinispan.interceptors.totalorder;

import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.interceptors.DistributionInterceptor;
import org.infinispan.remoting.transport.Address;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.Collection;

import static org.infinispan.interceptors.totalorder.TotalOrderHelper.totalOrderBroadcastPrepare;
import static org.infinispan.interceptors.totalorder.TotalOrderHelper.prepare;
import static org.infinispan.util.Util.getAffectedKeys;

/**
 * This interceptor handles distribution of entries across a cluster, as well as transparent lookup, when the
 * total order based protocol is enabled
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class TotalOrderDistributionInterceptor extends DistributionInterceptor implements TotalOrderHelper.TotalOrderRpcInterceptor {

   private static final Log log = LogFactory.getLog(TotalOrderDistributionInterceptor.class);

   @Override
   public Object visitPrepareCommand(TxInvocationContext ctx, PrepareCommand command) throws Throwable {
      return prepare(ctx, command, this);
   }

   @Override
   public Object visitPrepare(TxInvocationContext context, PrepareCommand command) throws Throwable {
      return super.visitPrepareCommand(context, command);
   }

   @Override
   protected void prepareOnAffectedNodes(TxInvocationContext ctx, PrepareCommand command, Collection<Address> recipients, boolean sync) {
      if(log.isTraceEnabled()) {
         log.tracef("Total Order Anycast transaction %s with Total Order", command.getGlobalTransaction().prettyPrint());
      }
      totalOrderBroadcastPrepare(command, recipients, getAffectedKeys(command, null), rpcManager, false,
                                 configuration.isSyncCommitPhase(), configuration.getSyncReplTimeout());
   }
}
