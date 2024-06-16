package io.pivio.server;

import java.io.IOException;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import io.pivio.server.elasticsearch.ElasticsearchConnectionAvailableChecker;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Profile("production")
public class CreateIndexOnStartupListener implements ApplicationListener<ContextRefreshedEvent> {

  private final ElasticsearchConnectionAvailableChecker elasticsearchConnectionAvailableChecker;
  private final IndexManager indexManager;

  public CreateIndexOnStartupListener(
      ElasticsearchConnectionAvailableChecker elasticsearchConnectionAvailableChecker,
      IndexManager manager) {
    this.elasticsearchConnectionAvailableChecker = elasticsearchConnectionAvailableChecker;
    this.indexManager = manager;
  }

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    if (!elasticsearchConnectionAvailableChecker.isConnectionToElasticsearchAvailable()) {
      throw createAndLogIllegalStateException(
          "cannot create Elasticsearch indices and mappings for PivioDocument and Changeset as no connection to Elasticsearch is available");
    }
    try {
      indexManager.setUpIndices();
    } catch (IOException e) {
      log.error("can't create indices due to: " + e.getMessage(), e);
    }
  }

  private RuntimeException createAndLogIllegalStateException(String message) {
    RuntimeException ise = new IllegalStateException(message);
    log.error(message, ise);
    return ise;
  }

}
