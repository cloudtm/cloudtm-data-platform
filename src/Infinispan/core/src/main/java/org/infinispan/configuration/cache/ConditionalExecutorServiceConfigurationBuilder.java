package org.infinispan.configuration.cache;

import org.infinispan.config.ConfigurationException;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class ConditionalExecutorServiceConfigurationBuilder
      extends AbstractConfigurationChildBuilder<ConditionalExecutorServiceConfiguration> {

   private int corePoolSize = 2;
   private int maxPoolSize = 16;
   private long keepAliveTime = 60000;

   protected ConditionalExecutorServiceConfigurationBuilder(ConfigurationBuilder builder) {
      super(builder);
   }

   public ConditionalExecutorServiceConfigurationBuilder corePoolSize(int corePoolSize) {
      this.corePoolSize = corePoolSize;
      return this;
   }

   public ConditionalExecutorServiceConfigurationBuilder maxPoolSize(int maxPoolSize) {
      this.maxPoolSize = maxPoolSize;
      return this;
   }

   public ConditionalExecutorServiceConfigurationBuilder keepAliveTime(long keepAliveTime) {
      this.keepAliveTime = keepAliveTime;
      return this;
   }

   @Override
   public ConfigurationChildBuilder read(ConditionalExecutorServiceConfiguration template) {
      this.corePoolSize = template.corePoolSize();
      this.maxPoolSize = template.maxPoolSize();
      this.keepAliveTime = template.keepAliveTime();
      return this;
   }

   @Override
   public String toString() {
      return "ConditionalExecutorServiceConfigurationBuilder{" +
            "corePoolSize=" + corePoolSize +
            ", maxPoolSize=" + maxPoolSize +
            ", keepAliveTime=" + keepAliveTime +
            '}';
   }

   @Override
   void validate() {
      if (corePoolSize <= 0) {
         throw new ConfigurationException("Core Pool Size should be greater than zero");
      }
      if (maxPoolSize < corePoolSize) {
         throw new ConfigurationException("Max Pool Size should be greater or equals than Core Pool Size");
      }
      if (keepAliveTime <= 0) {
         throw new ConfigurationException("Keep Alive Time should be greated than zero");
      }
   }

   @Override
   ConditionalExecutorServiceConfiguration create() {
      return new ConditionalExecutorServiceConfiguration(corePoolSize, maxPoolSize, keepAliveTime);
   }
}
