package io.pivio.server.elasticsearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.IdsQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.pit.CreatePitResponse;
import org.opensearch.client.opensearch.core.search.Pit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.pivio.server.document.PivioDocument;
import lombok.extern.log4j.log4j2;

@log4j2
@Component
public class ElasticsearchQueryHelper {

  @Autowired
  private ObjectMapper mapper;

  @Autowired
  private OpenSearchClient client;

  @Autowired
  public ObjectMapper objectMapper;

  @Value("#{pivioIndex}")
  private String pivioIndex;

  @Value("#{changesetIndex}")
  private String changesetIndex;

  private final SortOptions sortTimestampDesc = new SortOptions.Builder()
      .field(new FieldSort.Builder().field("timestamp").order(SortOrder.Desc).build()).build();
  private final Time stdScrollTime = Time.of(t -> t.offset(60000));

  public void searchByQuery() {
    MatchQuery matchQuery =
        MatchQuery.of(builder -> builder.field("movies").query(FieldValue.of("\"avengers\"")));

    FieldSort fieldSort =
        FieldSort.of(builder -> builder.field("release-date").order(SortOrder.Asc));
    SortOptions sortOptions = SortOptions.of(builder -> builder.field(fieldSort));

    SearchResponse<PivioDocument> searchResponse;
    try {
      searchResponse = client.search(s -> s.index("steckbrief").query(matchQuery.toQuery())
          .sort(sortOptions).size(0).from(100), PivioDocument.class);
      for (int i = 0; i < searchResponse.hits().hits().size(); i++) {
        System.out.println(searchResponse.hits().hits().get(i).source());
      }
    } catch (OpenSearchException | IOException e) {
      log.error("can't query OpnSearchServer due to " + e.getMessage(), e);
    }
  }

  public boolean isPivioPresent(String id) {
    try {
      GetResponse<JsonNode> response =
          client.get(g -> g.index("steckbrief").id(id), JsonNode.class);
      return response.source() != null;
    } catch (OpenSearchException | IOException e) {
      log.error("can't query OpnSearchServer due to " + e.getMessage(), e);
    }
    return false;
  }

  public boolean isDocumentPresent(String id) {
    List<String> _id = List.of(id);
    IdsQuery idsQuery = IdsQuery.of(builder -> builder.values(_id));
    SearchRequest searchRequest = SearchRequest.of(
        s -> s.index("steckbrief").from(0).size(_id.size()).query(Query.of(q -> q.ids(idsQuery))));
    try {
      SearchResponse<PivioDocument> response = client.search(searchRequest, PivioDocument.class);
      return !response.hits().hits().isEmpty();
    } catch (OpenSearchException | IOException e) {
      log.error("can't query OpenSearchServer due to " + e.getMessage(), e);
    }
    return false;
  }

  public Optional<JsonNode> isChangesetPresent(String id) {
    SearchRequest searchRequest = SearchRequest
        .of(s -> s.index(changesetIndex).from(0).size(1).query(q -> q.ids(ids -> ids.values(id))));
    try {
      SearchResponse<JsonNode> response = client.search(searchRequest, JsonNode.class);
      return Optional
          .ofNullable(response.documents().isEmpty() ? null : response.documents().getFirst());
    } catch (OpenSearchException | IOException e) {
      log.error("can't query OpnSearchServer due to " + e.getMessage(), e);
    }
    return Optional.empty();
  }

  public ArrayNode retrieveAllDocuments(Query searchQuery) throws IOException {
    CreatePitResponse createPitResponse =
        client.createPit(pit -> pit.keepAlive(stdScrollTime).targetIndexes(pivioIndex));
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

  public ArrayNode retrieveAllChangesets(Query searchQuery) throws IOException {
    CreatePitResponse createPitResponse =
        client.createPit(pit -> pit.keepAlive(stdScrollTime).targetIndexes(changesetIndex));
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
}
