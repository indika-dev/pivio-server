package io.pivio.server.document;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.joda.time.format.ISODateTimeFormat;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.query_dsl.IdsQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.pivio.server.changeset.ChangesetService;
import io.pivio.server.changeset.DocumentNotFoundException;
import io.pivio.server.elasticsearch.Changeset;
import lombok.extern.log4j.Log4j2;

@CrossOrigin
@RestController
@RequestMapping(value = "/document")
@Log4j2
public class DocumentController {

  private final ChangesetService changesetService;
  private final List<String> mandatoryFields =
      Arrays.asList("id", "type", "name", "owner", "description");
  private final JsonMapper mapper;
  private final OpenSearchClient client;

  @Autowired
  public ObjectMapper objectMapper;

  @Value("#{pivioIndex}")
  private String pivioIndex;

  @Value("#{changesetIndex}")
  private String changesetIndex;

  private Counter postDocumentCallsCounter;
  private Counter deleteDocumentCallCounter;
  private Counter getDocumentCallsCounter;

  public DocumentController(ChangesetService changesetService, JsonMapper mapper,
      MeterRegistry registry, OpenSearchClient client) {
    this.client = client;
    this.changesetService = changesetService;
    this.mapper = mapper;
    this.postDocumentCallsCounter = registry.counter("counter.calls.document.post");
    this.deleteDocumentCallCounter = registry.counter("counter.calls.document.id.delete");
    this.getDocumentCallsCounter = registry.counter("counter.calls.document.id.get");
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> create(@RequestBody ObjectNode document,
      UriComponentsBuilder uriBuilder) throws IOException {
    postDocumentCallsCounter.increment();
    if (isIdMissingOrEmpty(document)) {
      throw new DocumentNotFoundException(mapper.writeValueAsString(missingIdError(document)));
    }

    if (isMandatoryFieldMissingOrEmpty(document)) {
      throw new MandatoryFieldMissingOrEmptyException(
          mapper.writeValueAsString(missingMandatoryField(document)));
    }

    removeNullNodes(document);


    final Changeset changeset = changesetService.computeNext(document);
    final String documentId = document.get("id").asText();
    final SearchResponse<JsonNode> existingPivioDocumentResponse = client.search(
        request -> request.index(changesetIndex).from(0).size(100)
            .query(query -> query.ids(new IdsQuery.Builder().values(documentId).build())),
        JsonNode.class);
    final Optional<JsonNode> existingPivioDocument =
        Optional.ofNullable(existingPivioDocumentResponse.hits().hits().size() == 1
            ? objectMapper.readTree(existingPivioDocumentResponse.hits().hits().getFirst().node())
            : null);

    final String formattedChangeTime = ISODateTimeFormat.dateTime().print(changeset.getTimestamp());
    existingPivioDocument.ifPresentOrElse(persistedPivioDocument -> {
      document.put("created",
          getFieldOrElse(persistedPivioDocument, "created", formattedChangeTime));
      document.put("lastUpload", ISODateTimeFormat.dateTime().print(changeset.getTimestamp()));
      if (changeset.isEmpty()) {
        document.put("lastUpdate",
            getFieldOrElse(persistedPivioDocument, "lastUpdate", formattedChangeTime));
      } else {
        document.put("lastUpdate", formattedChangeTime);
      }
    }, () -> {
      document.put("created", formattedChangeTime);
      document.put("lastUpdate", formattedChangeTime);
      document.put("lastUpload", formattedChangeTime);
    });
    IndexResponse indexResponse =
        client.index(request -> request.index(pivioIndex).id(documentId).document(document));
    switch (indexResponse.result()) {
      case NoOp:
      case NotFound:
        throw new DocumentNotFoundException(
            document.get("id") + " wasn't indexed successfully: " + indexResponse.result());
      case Created:
      case Updated:
      case Deleted:
        log.info("indexed {} successfully with result: {}", document.get("id"),
            indexResponse.result());
    }
    if (!changeset.isEmpty()) {
      IndexResponse changesetIndexResponse =
          client.index(request -> request.index(changesetIndex).document(changeset));
      switch (changesetIndexResponse.result()) {
        case NoOp:
        case NotFound:
          throw new DocumentNotFoundException(
              document.get("id") + " wasn't indexed successfully: " + indexResponse.result());
        case Created:
        case Updated:
        case Deleted:
          log.info("indexed {} successfully with result: {}", document.get("id"),
              indexResponse.result());
      }
    }
    // log.info("Indexed document {} for {}", documentId, document.get("name").asText());
    return ResponseEntity
        .created(uriBuilder.path("/document/{documentId}").buildAndExpand(documentId).toUri())
        .build();
  }

  private JsonNode removeNullNodes(JsonNode node) {
    Iterator<JsonNode> iterator = node.iterator();
    while (iterator.hasNext()) {
      JsonNode next = iterator.next();
      if (next.getNodeType().equals(JsonNodeType.NULL)) {
        iterator.remove();
      }
      if (next.getNodeType().equals(JsonNodeType.ARRAY)
          || next.getNodeType().equals(JsonNodeType.OBJECT)) {
        JsonNode jsonNode = removeNullNodes(next);
        if (!jsonNode.iterator().hasNext()) {
          iterator.remove();
        }
      }
    }
    return node;
  }

  private JsonNode missingMandatoryField(JsonNode document) {
    ObjectNode error = mapper.createObjectNode();
    String missingMandatoryField = getMissingMandatoryField(document);
    if (missingMandatoryField != null) {
      log.info("Received document with missing mandatory field in {}", document.toString());
      error.put("error", "mandatory field '" + missingMandatoryField + "' is missing");
    } else {
      log.info("Received document with empty mandatory field in {}", document.toString());
      error.put("error", "mandatory field '" + getEmptyMandatoryField(document) + "' is empty");
    }
    return error;
  }

  private JsonNode missingIdError(JsonNode document) {
    log.info("Received document without or with empty id field in {}", document.toString());
    ObjectNode newId = mapper.createObjectNode();
    newId.put("id", UUID.randomUUID().toString());
    return newId;
  }

  private boolean isIdMissingOrEmpty(JsonNode document) {
    return document.get("id") == null || document.get("id").asText("") == null;
  }

  private String getFieldOrElse(JsonNode json, String fieldName, String defaultValue) {
    return json.has(fieldName) ? json.get(fieldName).textValue() : defaultValue;
  }

  private boolean isMandatoryFieldMissingOrEmpty(JsonNode document) {
    return getMissingMandatoryField(document) != null || getEmptyMandatoryField(document) != null;
  }

  private String getMissingMandatoryField(JsonNode document) {
    for (String field : mandatoryFields) {
      if (!document.has(field)) {
        return field;
      }
    }
    return null;
  }

  private String getEmptyMandatoryField(JsonNode document) {
    for (String field : mandatoryFields) {
      if (document.has(field) && document.get(field).asText("") != null) {
        return field;
      }
    }
    return null;
  }

  @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<JsonNode> get(@PathVariable String id) throws IOException {
    getDocumentCallsCounter.increment();
    List<String> _id = List.of(id);
    IdsQuery idsQuery = IdsQuery.of(builder -> builder.values(_id));
    SearchRequest searchRequest = SearchRequest.of(
        s -> s.index("steckbrief").from(0).size(_id.size()).query(Query.of(q -> q.ids(idsQuery))));
    try {
      SearchResponse<JsonNode> response = client.search(searchRequest, JsonNode.class);
      List<JsonNode> responseObjects = response.documents();
      if (responseObjects.isEmpty()) {
        return ResponseEntity.notFound().build();
      }
      return ResponseEntity.ok(responseObjects.get(0));
    } catch (OpenSearchException | IOException e) {
      log.error("can't query OpenSearchServer due to " + e.getMessage(), e);
      return ResponseEntity.status(HttpURLConnection.HTTP_UNAVAILABLE).build();
    }
  }

  @DeleteMapping(value = "/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) throws IOException {
    log.info("Try to delete document {}", id);
    deleteDocumentCallCounter.increment();
    log.info("Try to delete document {}", id);
    DeleteRequest request = new DeleteRequest.Builder().index("steckbrief").id(id).build();
    DeleteResponse response = client.delete(request);
    switch (response.result()) {
      case Deleted:
        DeleteRequest changesetRequest =
            new DeleteRequest.Builder().index("changeset").id(id).build();
        DeleteResponse changesetResponse = client.delete(changesetRequest);
        if (changesetResponse.result() == Result.Deleted) {

          log.info("Deleted document {} successfully", id);
          return ResponseEntity.noContent().build();
        } else {
          log.warn("Could not delete document {}", id);
          return ResponseEntity.notFound().build();
        }

      default:
        log.warn("Could not delete document {}", id);
        return ResponseEntity.notFound().build();
    }
  }
}
