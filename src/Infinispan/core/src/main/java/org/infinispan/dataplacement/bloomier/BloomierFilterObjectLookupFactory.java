/*
 * INESC-ID, Instituto de Engenharia de Sistemas e Computadores Investigação e Desevolvimento em Lisboa
 * Copyright 2013 INESC-ID and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3.0 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
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
