package org.infinispan.interceptors.totalorder;

import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.tx.VersionedPrepareCommand;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.interceptors.VersionedDistributionInterceptor;
import org.infinispan.remoting.transport.Address;
import org.infinispan.transaction.totalorder.TotalOrderManager;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.Collection;

import static org.infinispan.interceptors.totalorder.TotalOrderHelper.totalOrderBroadcastPrepare;
import static org.infinispan.interceptors.totalorder.TotalOrderHelper.TotalOrderRpcInterceptor;
import static org.infinispan.interceptors.totalorder.TotalOrderHelper.prepare;
import static org.infinispan.transaction.WriteSkewHelper.setVersionsSeenOnPrepareCommand;
import static org.infinispan.util.Util.getAffectedKeys;

/**
 * This interceptor is used in total order in distributed mode when the write skew check is enabled.
 * After sending the prepare through TOA (Total Order Anycast), it blocks the execution thread until the transaction
 * outcome is know (i.e., the write skew check passes in all keys owners)
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class TotalOrderVersionedDistributionInterceptor extends VersionedDistributionInterceptor implements TotalOrderRpcInterceptor {

   private static final Log log = LogFactory.getLog(TotalOrderVersionedDistributionInterceptor.class);

   private TotalOrderManager totalOrderManager;

   @Inject
   public void injectDependencies(TotalOrderManager totalOrderManager) {
      this.totalOrderManager = totalOrderManager;
   }

   @Override
   public Object visitPrepareCommand(TxInvocationContext ctx, PrepareCommand command) throws Throwable {
      return prepare(ctx, command, this);
   }

   @Override
   public Object visitPrepare(TxInvocationContext context, PrepareCommand command) throws Throwable {
      return super.visitPrepareCommand(context, command);
   }

   @Override
   protected void prepareOnAffectedNodes(TxInvocationContext ctx, PrepareCommand command,
                                         Collection<Address> recipients, boolean sync) {
      if(log.isTraceEnabled()) {
         log.tracef("Total Order Anycast transaction %s with Total Order", command.getGlobalTransaction().prettyPrint());
      }

      if (!(command instanceof VersionedPrepareCommand)) {
         throw new IllegalStateException("Expected a Versioned Prepare Command in version aware component");
      }

      setVersionsSeenOnPrepareCommand((VersionedPrepareCommand) command, ctx);
      totalOrderBroadcastPrepare(command, recipients, getAffectedKeys(command, null), rpcManager, false,
                                 configuration.isSyncCommitPhase(), configuration.getSyncReplTimeout());
   }
}
