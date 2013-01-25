package org.infinispan.distribution.wrappers;

/**
 * Websiste: www.cloudtm.eu
 * Date: 02/05/12
 * @author Diego Didona <didona@gsd.inesc-id.pt>
 * @since 5.2
 */
public class ReplCustomStatsInterceptor extends CustomStatsInterceptor {

   @Override
   public boolean isRemote(Object key) {
      return false;
   }
}
