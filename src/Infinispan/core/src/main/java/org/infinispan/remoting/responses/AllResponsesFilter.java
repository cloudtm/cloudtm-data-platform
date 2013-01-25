package org.infinispan.remoting.responses;

import org.infinispan.remoting.rpc.ResponseFilter;
import org.infinispan.remoting.transport.Address;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 4.0
 */
public class AllResponsesFilter implements ResponseFilter {

   @Override
   public boolean isAcceptable(Response response, Address sender) {
      return true;
   }

   @Override
   public boolean needMoreResponses() {
      return true;
   }
}
