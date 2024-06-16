package io.pivio.server.elasticsearch;

import java.io.IOException;
import java.util.List;
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
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.pivio.server.document.PivioDocument;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
public class ElasticsearchQueryHelper {

  private final ObjectMapper mapper;
  private final OpenSearchClient client;

  public ElasticsearchQueryHelper(ObjectMapper mapper, OpenSearchClient client) {
    this.mapper = mapper;
    this.client = client;
  }

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

  public boolean isChangesetPresent(String id) {
    List<String> _id = List.of(id);
    IdsQuery idsQuery = IdsQuery.of(idq -> idq.values(_id));
    SearchRequest searchRequest = SearchRequest.of(
        s -> s.index("changeset").from(0).size(_id.size()).query(Query.of(q -> q.ids(idsQuery))));
    try {
      SearchResponse<JsonNode> response = client.search(searchRequest, JsonNode.class);
      return !response.hits().hits().isEmpty();
    } catch (OpenSearchException | IOException e) {
      log.error("can't query OpnSearchServer due to " + e.getMessage(), e);
    }
    return false;
  }

  public ArrayNode retrieveAllDocuments(SearchRequest searchRequest) throws IOException {
    try {
      SearchResponse<JsonNode> searchResponse = client.search(searchRequest, JsonNode.class);
      ArrayNode allDocuments = mapper.createArrayNode();
      while (true) {
        searchResponse.hits().hits().forEach(hit -> {
          allDocuments.add(hit.node());
        });
        SearchResponse<JsonNode> scrollableResposne =
            client
                .scroll(
                    ScrollRequest.of(builder -> builder.scrollId(searchResponse.scrollId())
                        .scroll(Time.of(timeBuilder -> timeBuilder.offset(60000)))),
                    JsonNode.class);
        if (scrollableResposne.hits().hits().isEmpty()) {
          break;
        }
      }
      return allDocuments;
    } catch (Exception e) {
      log.warn("Could not retrieve all documents for " + searchRequest.toString(), e);
      return mapper.createArrayNode();
    }
  }
}
