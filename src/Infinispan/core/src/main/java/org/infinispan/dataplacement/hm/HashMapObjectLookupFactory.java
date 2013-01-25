package org.infinispan.dataplacement.hm;

import org.infinispan.configuration.cache.Configuration;
import org.infinispan.dataplacement.OwnersInfo;
import org.infinispan.dataplacement.lookup.ObjectLookup;
import org.infinispan.dataplacement.lookup.ObjectLookupFactory;

import java.util.Map;

/**
 * Object Lookup Factory when Hash Map based technique is used
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class HashMapObjectLookupFactory implements ObjectLookupFactory {

   @Override
   public void setConfiguration(Configuration configuration) {
      //nothing
   }

   @Override
   public ObjectLookup createObjectLookup(Map<Object, OwnersInfo> keysToMove, int numberOfOwners) {
      return new HashMapObjectLookup(keysToMove);
   }

   @Override
   public void init(ObjectLookup objectLookup) {
      //nothing to init
   }

   @Override
   public int getNumberOfQueryProfilingPhases() {
      return 1;
   }
}
