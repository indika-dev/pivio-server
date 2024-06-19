package io.pivio.server;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import org.junit.Before;
import org.opensearch.data.client.osc.OpenSearchTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pivio.server.changeset.ChangesetController;
import io.pivio.server.document.DocumentController;
import io.pivio.server.document.PivioDocument;
import io.pivio.server.elasticsearch.Changeset;

// @RunWith(SpringRunner.class)
// @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
// @ContextConfiguration(initializers = DockerEnvironmentInitializer.class)
public abstract class AbstractApiTestCase {

  protected static final String PIVIO_SERVER_BASE_URL = "http://localhost:9123";
  protected static final String SOME_ID = "someId";

  private static final Logger LOG = LoggerFactory.getLogger(AbstractApiTestCase.class);

  @Autowired
  protected OpenSearchTemplate elasticsearchTemplate;

  @Autowired
  protected RestTemplate restTemplate;

  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  private ChangesetController changesetController;

  @Autowired
  private DocumentController documentController;

  @Before
  public void waitUntilPivioServerIsUpAndCleanUpPersistentData() {
    waitUntilPivioServerIsUp();
    cleanUpPersistentData(elasticsearchTemplate);
  }

  private void waitUntilPivioServerIsUp() {
    await().atMost(180, SECONDS).until(() -> {
      String documentResponse = "";
      RestTemplate faultSensitiveRestTemplate =
          new RestTemplateBuilder().rootUri(PIVIO_SERVER_BASE_URL).build();
      try {
        documentResponse = faultSensitiveRestTemplate.getForObject("/document", String.class);
      } catch (Exception ignored) {
        LOG.debug("Pivio Server is not up yet. Exception message: {}", ignored.getMessage());
      }
      return !documentResponse.isEmpty();
    });
  }

  private void cleanUpPersistentData(OpenSearchTemplate elasticsearchTemplate) {
    LOG.debug(
        "Cleaning up persistent data from Elasticsearch: deleting indices, creating new ones, put mappings, refresh indices");

    IndexOperations documentIndexOps = elasticsearchTemplate.indexOps(PivioDocument.class);
    IndexOperations changesetIndexOps = elasticsearchTemplate.indexOps(Changeset.class);
    documentIndexOps.delete();
    changesetIndexOps.delete();

    assertTrue(documentIndexOps.create());
    assertTrue(documentIndexOps.putMapping(PivioDocument.class));

    assertTrue(changesetIndexOps.create());
    assertTrue(changesetIndexOps.putMapping(Changeset.class));

    documentIndexOps.refresh();
    changesetIndexOps.refresh();
  }

  private void refreshIndices() {}

  protected PivioDocument postDocumentWithSomeId() {
    return postDocumentWithId(SOME_ID);
  }

  protected PivioDocument postDocumentWithId(String id) {
    PivioDocument documentWithId = createDocumentWithId(id);
    postDocument(documentWithId);
    return documentWithId;
  }

  protected PivioDocument createDocumentWithId(String id) {
    return PivioDocument.builder().id(id).type("service").name("MicroService").serviceName("MS")
        .description("Super service...").owner("Awesome Team").build();
  }

  protected ResponseEntity<PivioDocument> postDocument(PivioDocument document) {
    return postDocument(document, PivioDocument.class);
  }

  protected ResponseEntity<JsonNode> postDocument(JsonNode document) {
    return postDocument(document, JsonNode.class);
  }

  protected <T> ResponseEntity<T> postDocument(Object document, Class<T> responseType) {
    ResponseEntity<T> responseEntity =
        restTemplate.postForEntity("/document", document, responseType);
    refreshIndices();
    return responseEntity;
  }

  // When provoking responses indicating client or server side HTTP errors (400, 500) we do not want
  // the test to fail.
  private static final class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {
    @Override
    public void handleError(ClientHttpResponse response) throws IOException {}
  }
}
