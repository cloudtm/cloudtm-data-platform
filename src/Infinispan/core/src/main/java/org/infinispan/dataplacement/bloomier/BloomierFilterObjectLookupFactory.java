package org.infinispan.dataplacement.bloomier;

import edu.utexas.ece.mpc.bloomier.ImmutableBloomierFilter;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.dataplacement.OwnersInfo;
import org.infinispan.dataplacement.lookup.ObjectLookup;
import org.infinispan.dataplacement.lookup.ObjectLookupFactory;
import org.infinispan.util.TypedProperties;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * An Object Lookup Factory that constructs {@link BloomierFilterObjectLookup}
 *
 * This implementation encodes the new owners in a bloomier filter
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class BloomierFilterObjectLookupFactory implements ObjectLookupFactory {

   private static final Log log = LogFactory.getLog(BloomierFilterObjectLookupFactory.class);

   //value size in bits
   private static final int VALUE_SIZE = 4 * 8;
   private static final String NUMBER_OF_HASHES = "numberOfHashes";
   private static final String TIMEOUT = "timeout";

   private int numberOfHashes = 10;
   private int timeout = 10000;

   @Override
   public void setConfiguration(Configuration configuration) {
      TypedProperties typedProperties = configuration.dataPlacement().properties();

      numberOfHashes = getIntProperty(typedProperties, NUMBER_OF_HASHES, numberOfHashes);
      timeout = getIntProperty(typedProperties, TIMEOUT, timeout);
   }

   @SuppressWarnings("unchecked")
   @Override
   public ObjectLookup createObjectLookup(Map<Object, OwnersInfo> keysToMove, int numberOfOwners) {
      ImmutableBloomierFilter[] bloomierFilters = new ImmutableBloomierFilter[numberOfOwners];
      for (int i = 0; i < numberOfOwners; ++i) {
         Map<Object, Integer> map = split(keysToMove, i);
         try {
            bloomierFilters[i] = createBloomierFilter(map);
         } catch (TimeoutException e) {
            return null;
         }
      }
      return new BloomierFilterObjectLookup(bloomierFilters);
   }

   @Override
   public void init(ObjectLookup objectLookup) {
      //no-op
   }

   @Override
   public int getNumberOfQueryProfilingPhases() {
      return 1;
   }

   private Map<Object, Integer> split(Map<Object, OwnersInfo> keysToMove, int iteration) {
      Map<Object, Integer> map = new HashMap<Object, Integer>();
      for (Map.Entry<Object, OwnersInfo> entry : keysToMove.entrySet()) {
         map.put(entry.getKey(), entry.getValue().getOwner(iteration));
      }
      return map;
   }

   private ImmutableBloomierFilter<Object, Integer> createBloomierFilter(Map<Object, Integer> newOwners) throws TimeoutException {
      int numberOfKeys = newOwners.size() * 4;
      return new ImmutableBloomierFilter<Object, Integer>(newOwners, numberOfKeys, numberOfHashes, VALUE_SIZE,
                                                          Integer.class, timeout);
   }

   private int getIntProperty(TypedProperties properties, String propertyName, int defaultValue) {
      try {
         String tmp = properties.getProperty(propertyName, Integer.toString(defaultValue));
         return Integer.parseInt(tmp);
      } catch (NumberFormatException nfe) {
         log.warnf("Error parsing parsing property %s. The default value is %s. %s", propertyName, defaultValue,
                   nfe.getMessage());
      }
      return defaultValue;
   }
}
