package io.pivio.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * PivioServerConfig
 */
@Configuration
public class PivioServerConfig {

  @Bean
  String changesetIndex(@Value("${changesetIndex:changeset}") String indexName) {
    return indexName;
  }

  @Bean
  String pivioIndex(@Value("${pivioIndex:steckbrief}") String indexName) {
    return indexName;
  }

  @Bean
  MeterRegistryCustomizer<MeterRegistry> meterRegistryCustomizer() {
    return registry -> {
      registry.config().namingConvention().name("counter.calls.changeset.get", Type.COUNTER);
      registry.config().namingConvention().name("counter.calls.document.id.changeset.get",
          Type.COUNTER);
      registry.config().namingConvention().name("counter.calls.document.post", Type.COUNTER);
      registry.config().namingConvention().name("counter.calls.document.id.delete", Type.COUNTER);
      registry.config().namingConvention().name("counter.calls.document.get", Type.COUNTER);
    };
  }

  @Bean
  ObjectMapper jsonMapper() {
    return new ObjectMapper(new JsonFactoryBuilder().build());
  }
}
