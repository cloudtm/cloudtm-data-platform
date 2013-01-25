package org.infinispan.remoting.responses;

import org.infinispan.remoting.rpc.ResponseFilter;
import org.infinispan.remoting.transport.Address;

import java.util.Collection;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 4.0
 */
public class KeyValidationFilter implements ResponseFilter {

   private final Collection<Object> keysNeededValidation;
   private final Address localAddress;
   private boolean exception;
   private boolean selfDelivered;

   public KeyValidationFilter(Collection<Object> keysNeededValidation, Address localAddress) {
      this.keysNeededValidation = keysNeededValidation;
      this.localAddress = localAddress;
      this.exception = false;
      this.selfDelivered = false;
   }


   @Override
   public boolean isAcceptable(Response response, Address sender) {
      if (response instanceof ExceptionResponse) {
         exception = true;
      } else if (response instanceof SuccessfulResponse) {
         Object retVal = ((SuccessfulResponse) response).getResponseValue();
         if (retVal instanceof Collection<?>) {
            keysNeededValidation.removeAll((Collection<?>) retVal);
         }
      } else if (localAddress.equals(sender)) {
         selfDelivered = true;
      }
      return true;
   }

   @Override
   public boolean needMoreResponses() {
      return !selfDelivered || (!keysNeededValidation.isEmpty() && !exception);
   }
}
