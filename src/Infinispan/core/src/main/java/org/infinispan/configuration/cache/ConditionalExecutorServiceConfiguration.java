package org.infinispan.configuration.cache;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class ConditionalExecutorServiceConfiguration {

   private final int corePoolSize;
   private final int maxPoolSize;
   private final long keepAliveTime;

   public ConditionalExecutorServiceConfiguration(int corePoolSize, int maxPoolSize, long keepAliveTime) {
      this.corePoolSize = corePoolSize;
      this.maxPoolSize = maxPoolSize;
      this.keepAliveTime = keepAliveTime;
   }

   public int corePoolSize() {
      return corePoolSize;
   }

   public int maxPoolSize() {
      return maxPoolSize;
   }

   public long keepAliveTime() {
      return keepAliveTime;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ConditionalExecutorServiceConfiguration that = (ConditionalExecutorServiceConfiguration) o;

      return corePoolSize == that.corePoolSize &&
            keepAliveTime == that.keepAliveTime &&
            maxPoolSize == that.maxPoolSize;

   }

   @Override
   public int hashCode() {
      int result = corePoolSize;
      result = 31 * result + maxPoolSize;
      result = 31 * result + (int) (keepAliveTime ^ (keepAliveTime >>> 32));
      return result;
   }

   @Override
   public String toString() {
      return "ConditionalExecutorServiceConfiguration{" +
            "corePoolSize=" + corePoolSize +
            ", maxPoolSize=" + maxPoolSize +
            ", keepAliveTime=" + keepAliveTime +
            '}';
   }
}
