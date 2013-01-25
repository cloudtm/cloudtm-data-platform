package org.infinispan.container;

import org.infinispan.container.entries.CacheEntry;
import org.infinispan.container.versioning.EntryVersion;
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
public class VersionedCommitContextEntries extends NonVersionedCommitContextEntries {

   private final Log log = LogFactory.getLog(VersionedCommitContextEntries.class);

   @Override
   protected Log getLog() {
      return log;
   }

   @Override
   protected void commitContextEntry(CacheEntry entry, InvocationContext ctx, boolean skipOwnershipCheck) {
      if (ctx.isInTxScope()) {
         EntryVersion version = ((TxInvocationContext) ctx).getCacheTransaction().getUpdatedEntryVersions().get(entry.getKey());
         commitEntry(entry, version, skipOwnershipCheck);
      } else {
         // This could be a state transfer call!
         commitEntry(entry, entry.getVersion(), skipOwnershipCheck);
      }
   }
}
