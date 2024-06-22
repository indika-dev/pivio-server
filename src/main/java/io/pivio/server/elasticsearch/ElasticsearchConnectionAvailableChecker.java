package io.pivio.server.elasticsearch;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.springframework.stereotype.Component;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class ElasticsearchConnectionAvailableChecker {

  private OpenSearchClient client;

  public ElasticsearchConnectionAvailableChecker(OpenSearchClient client) {
    this.client = client;
  }

  public boolean isConnectionToElasticsearchAvailable() {
    return checkNowThenIn5secThen10secThen20sec(this::isTransportClientConnectedToNode);
  }

  private boolean checkNowThenIn5secThen10secThen20sec(Supplier<Boolean> check) {
    for (int numberOfTriesFailed = 0; numberOfTriesFailed < 3; numberOfTriesFailed++) {
      if (check.get()) {
        return true;
      }
      int exponentialMultiplier = (int) Math.pow(2, numberOfTriesFailed);
      waitInSeconds(exponentialMultiplier * 5);
    }
    return false;
  }

  public boolean isTransportClientConnectedToNode() {
    try {
      return client.ping().value();
    } catch (OpenSearchException | IOException e) {
      return false;
    }
  }

  private void waitInSeconds(int secondsToWait) {
    log.warn(
        "No connection to Elasticsearch available. TransportClient tries to connect during the next {}s.",
        secondsToWait);
    try {
      TimeUnit.SECONDS.sleep(secondsToWait);
    } catch (InterruptedException e) {
      log.warn("Letting TransportClient try to connect to an Elasticsearch node within "
          + secondsToWait + "s has been interrupted", e);
    }
  }
}
