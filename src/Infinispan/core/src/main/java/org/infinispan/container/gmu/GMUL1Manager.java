package org.infinispan.container.gmu;

import org.infinispan.distribution.L1Manager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.util.concurrent.NoOpFuture;
import org.infinispan.util.concurrent.NotifyingNotifiableFuture;

import java.util.Collection;
import java.util.concurrent.Future;

/**
 * GMU does not need to keep track of the requestors
 *
 * @author Hugo Pimentel
 * @author Pedro Ruivo
 * @since 5.2
 */
public class GMUL1Manager implements L1Manager {

   @Override
   public void addRequestor(Object key, Address requestor) {
      //no-op
   }

   @Override
   public NotifyingNotifiableFuture<Object> flushCache(Collection<Object> keys, Object retval, Address origin, boolean assumeOriginKeptEntryInL1) {
      return new NoOpFuture<Object>(retval);
   }

   @Override
   public Future<Object> flushCacheWithSimpleFuture(Collection<Object> keys, Object retval, Address origin, boolean assumeOriginKeptEntryInL1) {
      return new NoOpFuture<Object>(retval);
   }
}
