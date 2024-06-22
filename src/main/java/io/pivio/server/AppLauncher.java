package io.pivio.server;

import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;

@Log4j2
@SpringBootApplication(exclude = {ElasticsearchDataAutoConfiguration.class},
    scanBasePackageClasses = {PivioServerConfig.class})
public class AppLauncher {

  @Autowired
  private OpenSearchClient esOps;

  @PreDestroy
  public void deleteIndex() {
    try {
      esOps.indices().delete(builder -> builder.index("steckbrief"));
    } catch (OpenSearchException | IOException e) {
      log.warn("can't delete index steckbrief: " + e.getMessage(), e);
    }
    try {
      esOps.indices().delete(builder -> builder.index("changeset"));
    } catch (OpenSearchException | IOException e) {
      log.warn("can't delete index changeset: " + e.getMessage(), e);
    }
  }

  @PostConstruct
  public void prepareDb() {
    try {
      esOps.indices().create(builder -> builder.index("steckbrief"));
    } catch (OpenSearchException | IOException e) {
      log.warn("can't create index steckbrief: " + e.getMessage(), e);
    }
    try {
      esOps.indices().create(builder -> builder.index("changeset"));
    } catch (OpenSearchException | IOException e) {
      log.warn("can't create index changeset: " + e.getMessage(), e);
    }
  }

  public static void main(String[] args) throws Exception {
    SpringApplication.run(AppLauncher.class, args);
  }
}
