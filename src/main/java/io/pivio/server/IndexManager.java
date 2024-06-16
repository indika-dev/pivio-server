package io.pivio.server;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.BooleanProperty;
import org.opensearch.client.opensearch._types.mapping.DateProperty;
import org.opensearch.client.opensearch._types.mapping.GeoPointProperty;
import org.opensearch.client.opensearch._types.mapping.IntegerNumberProperty;
import org.opensearch.client.opensearch._types.mapping.KeywordProperty;
import org.opensearch.client.opensearch._types.mapping.ObjectProperty;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TextProperty;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.stream.JsonParser;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class IndexManager {

  @Autowired
  private OpenSearchClient searchClient;

  public void setUpIndices() throws IOException {

    log.info("Creating index for documents");
    create("steckbrief", "steckbrief-index-opensearch.json");
    log.info("Creating index for changesets");
    create("changeset", "changeset-index-opensearch.json");
  }

  protected void create(String indexName, String indexFilename) throws IOException {

    DeleteIndexRequest deleteIndexRequest =
        new DeleteIndexRequest.Builder().index(indexName).build();
    searchClient.indices().delete(deleteIndexRequest);

    JsonpMapper mapper = searchClient._transport().jsonpMapper();
    JsonNode indexDef = new ObjectMapper()
        .readTree(new InputStreamReader(
            Objects.requireNonNull(this.getClass().getResourceAsStream(indexFilename))))
        .findPath("/" + indexName);

    try (
        JsonParser settingsParser = mapper.jsonProvider()
            .createParser(new StringReader(indexDef.get("settings").toString()));
        JsonParser mappingsParser = mapper.jsonProvider()
            .createParser(new StringReader(indexDef.get("mappings").toString()));) {
      IndexSettings indexSettings = IndexSettings._DESERIALIZER.deserialize(settingsParser, mapper);
      TypeMapping typeMapping = TypeMapping._DESERIALIZER.deserialize(mappingsParser, mapper);
      CreateIndexRequest indexRequest = new CreateIndexRequest.Builder().index(indexName)
          .settings(indexSettings).mappings(typeMapping).build();
      CreateIndexResponse indexResponse = searchClient.indices().create(indexRequest); // line 93

      log.info("Index {} created with response [{}]", indexName, indexResponse.toString());
    }
  }


  @SneakyThrows
  public static TypeMapping typeMapping() {
    TypeMapping.Builder builder = new TypeMapping.Builder();
    String sourceJson = "";

    builder.enabled(true);
    builder.dateDetection(true);

    Map<String, Property> properties = new LinkedHashMap<String, Property>();
    Iterator<Entry<String, JsonNode>> jsonNodeIterator =
        new ObjectMapper().readTree(sourceJson).get("mappings").get("properties").fields();
    while (jsonNodeIterator.hasNext()) {
      Entry<String, JsonNode> node = jsonNodeIterator.next();

      Property.Builder property = new Property.Builder();
      String type = node.getValue().get("type").asText();
      String format =
          node.getValue().hasNonNull("date") ? node.getValue().get("date").asText(null) : null;
      boolean index =
          node.getValue().hasNonNull("date") ? node.getValue().get("index").asBoolean(false) : null;

      switch (type) {
        case "boolean" -> property.boolean_(new BooleanProperty.Builder().index(index).build());
        case "text" -> property.text(new TextProperty.Builder().index(index).build());
        case "keyword" -> property.keyword(new KeywordProperty.Builder().index(index).build());
        case "date" -> property.date(new DateProperty.Builder().format(format).build());
        case "integer" -> property
            .integer(new IntegerNumberProperty.Builder().index(index).build());
        case "geo_pont" -> property
            .geoPoint(new GeoPointProperty.Builder().ignoreMalformed(true).build());
        case "object" -> property.object(new ObjectProperty.Builder().enabled(true).build());
        default -> throw new IllegalArgumentException("No mapping created for type:" + type);
      }

      properties.put(node.getKey(), property.build());
    }

    builder.properties(properties);

    return builder.build();
  }
}
