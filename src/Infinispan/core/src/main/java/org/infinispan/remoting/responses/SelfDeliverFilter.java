package org.infinispan.remoting.responses;

import org.infinispan.remoting.rpc.ResponseFilter;
import org.infinispan.remoting.transport.Address;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class SelfDeliverFilter implements ResponseFilter {

   private final Address localAddress;
   private boolean received;

   public SelfDeliverFilter(Address localAddress) {
      this.localAddress = localAddress;
      this.received = false;
   }

   @Override
   public boolean isAcceptable(Response response, Address sender) {
      if (sender.equals(localAddress)) {
         received = true;
      }
      return true;
   }

   @Override
   public boolean needMoreResponses() {
      return !received;
   }
}
