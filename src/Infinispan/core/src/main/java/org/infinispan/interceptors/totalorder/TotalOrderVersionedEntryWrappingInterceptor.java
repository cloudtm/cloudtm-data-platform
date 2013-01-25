package org.infinispan.interceptors.totalorder;

import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.tx.VersionedPrepareCommand;
import org.infinispan.commands.write.WriteCommand;
import org.infinispan.container.entries.ClusteredRepeatableReadEntry;
import org.infinispan.container.entries.MVCCEntry;
import org.infinispan.container.versioning.EntryVersion;
import org.infinispan.container.versioning.EntryVersionsMap;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.factories.annotations.Start;
import org.infinispan.interceptors.VersionedEntryWrappingInterceptor;
import org.infinispan.transaction.WriteSkewException;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Wrapping Interceptor for Total Order protocol with versioning
 *
 * @author Mircea.Markus@jboss.com
 * @since 5.2
 */
public class TotalOrderVersionedEntryWrappingInterceptor extends VersionedEntryWrappingInterceptor {

   private static final Log log = LogFactory.getLog(TotalOrderVersionedEntryWrappingInterceptor.class);
   private boolean trace;

   @Start
   public void setLogLevel() {
      trace = log.isTraceEnabled();
   }

   @Override
   public final Object visitPrepareCommand(TxInvocationContext ctx, PrepareCommand command) throws Throwable {

      if (ctx.isOriginLocal()) {
         Object retVal = invokeNextInterceptor(ctx, command);
         if (shouldCommitEntries(command, ctx)) {
            commitContextEntries.commitContextEntries(ctx);
         }
         return retVal;
      }

      VersionCheckWrappingEntryVisitor visitor = new VersionCheckWrappingEntryVisitor(command);

      //both wraps and performs the write skew check
      if (!ctx.isOriginLocal() || command.isReplayEntryWrapping()) {
         for (WriteCommand c : command.getModifications())
            c.acceptVisitor(ctx, visitor);
      }

      invokeNextInterceptor(ctx, command);
      if (command.isOnePhaseCommit()) {
         commitContextEntries.commitContextEntries(ctx);
      } else {
         if (trace)
            log.tracef("Transaction %s will be committed in the 2nd phase", ctx.getGlobalTransaction().prettyPrint());
      }

      return configuration.getCacheMode().isReplicated() ? null : visitor.getKeysValidated();
   }

   public class VersionCheckWrappingEntryVisitor extends EntryWrappingVisitor {

      private final VersionedPrepareCommand prepareCommand;
      private final Set<Object> keysValidated;

      public VersionCheckWrappingEntryVisitor(PrepareCommand command) {
         this.prepareCommand = (VersionedPrepareCommand) command;
         this.keysValidated = new HashSet<Object>();
      }

      public Set<Object> getKeysValidated() {
         return keysValidated;
      }

      @Override
      protected final MVCCEntry wrapEntryForReplace(InvocationContext ctx, Object key) throws InterruptedException {
         return checkForWriteSkew(super.wrapEntryForReplace(ctx, key));
      }

      @Override
      protected final MVCCEntry wrapEntryForRemove(InvocationContext ctx, Object key) throws InterruptedException {
         MVCCEntry mvccEntry = super.wrapEntryForRemove(ctx, key);
         if (mvccEntry != null) {
            return checkForWriteSkew(mvccEntry);
         } else {
            return null;
         }
      }

      @Override
      protected final MVCCEntry wrapEntryForClear(InvocationContext ctx, Object key) throws InterruptedException {
         return checkForWriteSkew(super.wrapEntryForClear(ctx, key));
      }

      @Override
      protected final MVCCEntry wrapEntryForPut(InvocationContext ctx, Object key, boolean putIfAbsent) throws InterruptedException {
         return checkForWriteSkew(super.wrapEntryForPut(ctx, key, putIfAbsent));
      }

      private MVCCEntry checkForWriteSkew(MVCCEntry mvccEntry) {
         ClusteredRepeatableReadEntry clusterMvccEntry = (ClusteredRepeatableReadEntry) mvccEntry;

         EntryVersionsMap versionsSeen = prepareCommand.getVersionsSeen();
         EntryVersion versionSeen = versionsSeen.get(clusterMvccEntry.getKey());

         if (versionSeen != null) {
            clusterMvccEntry.setVersion(versionSeen);
         }

         if (!clusterMvccEntry.performWriteSkewCheck(dataContainer)) {
            throw WriteSkewException.createException(mvccEntry.getKey(), dataContainer.get(mvccEntry.getKey(), null),
                                                     mvccEntry, prepareCommand.getGlobalTransaction());
         }
         keysValidated.add(clusterMvccEntry.getKey());
         return clusterMvccEntry;
      }
   }
}
