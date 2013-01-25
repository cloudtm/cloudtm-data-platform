package org.infinispan.statetransfer;

import java.io.Serializable;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class ConfigurationState implements Serializable {

   private final int numberOfOwners;
   private final String protocolName;
   private final long epoch;

   public ConfigurationState(int numberOfOwners, String protocolName, long epoch) {
      this.numberOfOwners = numberOfOwners;
      this.protocolName = protocolName;
      this.epoch = epoch;
   }

   public int getNumberOfOwners() {
      return numberOfOwners;
   }

   public String getProtocolName() {
      return protocolName;
   }

   public long getEpoch() {
      return epoch;
   }

   @Override
   public String toString() {
      return "ConfigurationState{" +
            "numberOfOwners=" + numberOfOwners +
            ", protocolName='" + protocolName + '\'' +
            ", epoch=" + epoch +
            '}';
   }
}
