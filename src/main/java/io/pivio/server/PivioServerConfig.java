package io.pivio.server;

import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * PivioServerConfig
 */
@Configuration
public class PivioServerConfig {

  @Bean
  MeterRegistryCustomizer<MeterRegistry> getChangeSetRegistry() {
    return registry -> registry.config().namingConvention().name("counter.calls.changeset.get",
        Type.COUNTER);
  }

  @Bean
  MeterRegistryCustomizer<MeterRegistry> getDocumentIdChangeSetCounter() {
    return registry -> registry.config().namingConvention()
        .name("counter.calls.document.id.changeset.get", Type.COUNTER);
  }

  @Bean
  MeterRegistryCustomizer<MeterRegistry> postDocumentCallsCounter() {
    return registry -> registry.config().namingConvention().name("counter.calls.document.post",
        Type.COUNTER);
  }

  @Bean
  MeterRegistryCustomizer<MeterRegistry> deleteDocumentCallsCounter() {
    return registry -> registry.config().namingConvention().name("counter.calls.document.id.delete",
        Type.COUNTER);
  }

  @Bean
  MeterRegistryCustomizer<MeterRegistry> getDocumentCallsCounter() {
    return registry -> registry.config().namingConvention().name("counter.calls.document.get",
        Type.COUNTER);
  }

  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        // enable CORS for all domains, remember to adapt this on production scenarios
        registry.addMapping("/**").allowedOrigins("*");
      }
    };
  }

}
