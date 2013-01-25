package org.infinispan.container;

import org.infinispan.container.entries.CacheEntry;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class GMUCommitContextEntries extends NonVersionedCommitContextEntries {

   private final Log log = LogFactory.getLog(GMUCommitContextEntries.class);

   @Override
   protected Log getLog() {
      return log;
   }

   @Override
   protected void commitContextEntry(CacheEntry entry, InvocationContext ctx, boolean skipOwnershipCheck) {
      if (ctx.isInTxScope()) {
         commitEntry(entry, ((TxInvocationContext)ctx).getTransactionVersion(), skipOwnershipCheck);
      } else {
         commitEntry(entry, entry.getVersion(), skipOwnershipCheck);
      }
   }
}
