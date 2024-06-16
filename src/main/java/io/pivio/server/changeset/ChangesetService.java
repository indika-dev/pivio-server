package io.pivio.server.changeset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import io.pivio.server.elasticsearch.Changeset;
import io.pivio.server.elasticsearch.Fields;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
public class ChangesetService {

  private final ChangesetRepository changesetRepository;
  private final ObjectMapper mapper;
  private final Set<String> excludedFields;

  public ChangesetService(ChangesetRepository repository, ObjectMapper mapper) {
    this.mapper = mapper;
    this.changesetRepository = repository;
    excludedFields = new HashSet<>();
    excludedFields.add("/created");
    excludedFields.add("/lastUpload");
    excludedFields.add("/lastUpdate");
  }

  public Changeset computeNext(JsonNode document) throws IOException {
    final String documentId = document.get("id").asText();
    final Optional<Changeset> persistentDocument = getDocument(documentId);
    final JsonNode patch = JsonDiff.asJson(
        mapper.valueToTree(persistentDocument.orElse(Changeset.builder().build())), document);
    return Changeset.builder().document(documentId).order(retrieveLastOrderNumber(documentId) + 1L)
        .fields(filterExcludedFields(patch)).timestamp(DateTime.now()).build();
  }

  private List<Fields> filterExcludedFields(JsonNode json) {
    List<Fields> filteredJson = new ArrayList<>();
    Iterator<JsonNode> elements = json.elements();
    while (elements.hasNext()) {
      JsonNode current = elements.next();
      if (current.has("path") && !excludedFields.contains(current.get("path").textValue())) {
        try {
          filteredJson.add(mapper.treeToValue(current, Fields.class));
        } catch (JsonProcessingException | IllegalArgumentException e) {
          log.error("can't parse " + current.toPrettyString() + " due to " + e.getMessage(), e);
        }
      }
    }
    return filteredJson;
  }

  private long retrieveLastOrderNumber(String documentId) throws IOException {
    Optional<Changeset> lastChangeset = getLastChangeset(documentId);
    return lastChangeset.map(c -> c.getOrder()).orElse(0L);
  }

  private Optional<Changeset> getDocument(String id) throws IOException {
    Optional<Changeset> response = changesetRepository.findById(id);
    if (response.isPresent()) {
      return response;
    } else {
      throw new DocumentNotFoundException(id);
    }
  }

  private Optional<Changeset> getLastChangeset(String documentId) throws IOException {
    return changesetRepository.findById(documentId);
  }
}
