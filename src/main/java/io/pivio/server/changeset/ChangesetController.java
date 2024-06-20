package io.pivio.server.changeset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.pit.CreatePitResponse;
import org.opensearch.client.opensearch.core.search.Pit;
import org.opensearch.data.client.osc.OpenSearchTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.pivio.server.elasticsearch.ElasticsearchQueryHelper;

@CrossOrigin
@RestController
public class ChangesetController {

  private static final Logger LOG = LoggerFactory.getLogger(ChangesetController.class);

  private final ChangesetRepository repository;
  private final Counter getChangeSetCounter;
  private final Counter docIdChangeSetCounter;
  private final OpenSearchClient client;
  private final ElasticsearchQueryHelper queryHelper;
  private final ObjectMapper mapper;

  private final SortOptions sortTimestampDesc = new SortOptions.Builder()
      .field(new FieldSort.Builder().field("timestamp").order(SortOrder.Desc).build()).build();
  private final Time stdScrollTime = Time.of(t -> t.offset(60000));

  public ChangesetController(ChangesetRepository repository, OpenSearchTemplate template,
      IndexQueryBuilder queryBuilder, MeterRegistry registry, OpenSearchClient client,
      ElasticsearchQueryHelper queryHelper, ObjectMapper mapper) {
    this.repository = repository;
    this.queryHelper = queryHelper;
    this.client = client;
    this.getChangeSetCounter = registry.counter("counter.calls.changeset.get");
    this.docIdChangeSetCounter = registry.counter("counter.calls.document.id.changeset.get");
    this.mapper = mapper;
  }

  @GetMapping(value = "/changeset", produces = MediaType.APPLICATION_JSON_VALUE)
  public ArrayNode listAll(@RequestParam(required = false) String since) throws IOException {
    getChangeSetCounter.increment();
    if (!isSinceParameterValid(since)) {
      LOG.info("Received changeset request with invalid since parameter in {} for all documents",
          since);
      throw new InvalidSinceParameterException(since);
    }

    LOG.debug("Retrieving changesets for all documents with since parameter {}", since);
    return getSearchResponse(createQuery(since));
  }

  @GetMapping(value = "/document/{id}/changeset", produces = MediaType.APPLICATION_JSON_VALUE)
  public ArrayNode get(@PathVariable String id, @RequestParam(required = false) String since)
      throws IOException {
    docIdChangeSetCounter.increment();

    if (repository.findById(id).isEmpty()) {
      LOG.info("Client wants to retrieve changesets for missing document with id {}", id);
      throw new DocumentNotFoundException(id);
    }

    if (!isSinceParameterValid(since)) {
      LOG.info("Received changeset request with invalid since parameter in {} for document {}",
          since, id);
      throw new InvalidSinceParameterException(since);
    }

    LOG.debug("Retrieving changesets for document {} with since parameter {}", id, since);
    return getSearchResponse(createQuery(id, since));
  }

  private ArrayNode getSearchResponse(Query searchQuery) throws IOException {
    CreatePitResponse createPitResponse =
        client.createPit(pit -> pit.keepAlive(stdScrollTime).targetIndexes("changeset"));
    ArrayNode allDocuments = mapper.createArrayNode();
    List<JsonNode> currentResultPage = new ArrayList<>(100);
    Pit queryPit = Pit.of(builder -> builder.id(createPitResponse.pitId()));
    SearchResponse<JsonNode> searchResponse = client.search(
        request -> request.size(100).query(searchQuery).sort(sortTimestampDesc).pit(queryPit),
        JsonNode.class);
    while (!(currentResultPage = searchResponse.documents()).isEmpty()) {
      allDocuments.addAll(currentResultPage);
      String searchAfterParam = currentResultPage.getLast().get("timestamp").textValue();
      searchResponse = client.search(request -> request.size(100).query(searchQuery)
          .sort(sortTimestampDesc).pit(queryPit).searchAfter(searchAfterParam), JsonNode.class);
    }
    client.deletePit(request -> request.pitId(List.of(createPitResponse.pitId())));
    return allDocuments;
  }

  private Query createQuery(String since) {
    if (since == null) {
      return new Query.Builder().matchAll(new MatchAllQuery.Builder().build()).build();
    } else {
      JsonData sinceDate = calculateSinceDate(since);
      return new Query.Builder().range(new RangeQuery.Builder().field("timestamp").gte(sinceDate)
          .lte(JsonData.of("now")).build()).build();
    }
  }

  private Query createQuery(String id, String since) {
    Query matchDocumentId = new Query.Builder()
        .match(new MatchQuery.Builder().field("document").query(q -> q.stringValue(id)).build())
        .build();
    if (since == null) {
      return matchDocumentId;
    } else {
      JsonData sinceDate = calculateSinceDate(since);
      Query rangeQuery = new Query.Builder().range(new RangeQuery.Builder().field("timestamp")
          .gte(sinceDate).lte(JsonData.of("now")).build()).build();
      Query matchAndSinceQuery = new Query.Builder()
          .bool(new BoolQuery.Builder().must(matchDocumentId, rangeQuery).build()).build();
      return matchAndSinceQuery;
    }
  }

  private boolean isSinceParameterValid(String since) {
    if (since == null) {
      return true;
    }
    if (since.length() < 2) {
      return false;
    }
    if (!(since.charAt(since.length() - 1) == 'd' || since.charAt(since.length() - 1) == 'w')) {
      return false;
    }

    try {
      int sinceValue = Integer.parseInt(since.substring(0, since.length() - 1));
      return sinceValue > 0;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private JsonData calculateSinceDate(String since) {
    final DateTime sinceDate;
    if (since.charAt(since.length() - 1) == 'd') {
      sinceDate =
          DateTime.now().minusDays(Integer.parseInt(since.substring(0, since.length() - 1)));
    } else {
      sinceDate =
          DateTime.now().minusWeeks(Integer.parseInt(since.substring(0, since.length() - 1)));
    }
    return JsonData.of(sinceDate); // ISODateTimeFormat.date().print(sinceDate);
  }
}
