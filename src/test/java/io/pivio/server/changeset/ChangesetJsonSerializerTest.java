package io.pivio.server.changeset;

import static org.assertj.core.api.Assertions.assertThat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.http.HttpHost;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pivio.server.elasticsearch.Changeset;
import io.pivio.server.elasticsearch.Fields;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@JsonTest
public class ChangesetJsonSerializerTest {

  @Configuration
  public static class Config {

    @Bean
    public OpenSearchClient client() {
      // Create the low-level client
      RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200)).build();

      // Create the transport with a Jackson mapper
      OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());

      // And create the API client
      OpenSearchClient client = new OpenSearchClient(transport);
      return client;
    }

    @Bean
    public ObjectMapper objectMapper() {
      return new ObjectMapper(new JsonFactory());
    }
  }

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  public void shouldSerializeDocumentId() throws JsonProcessingException {
    Changeset changeset =
        Changeset.builder().document("randomId").order(1L).fields(Collections.emptyList()).build();
    JsonNode serializedJson = objectMapper.valueToTree(changeset);
    assertThat(serializedJson.get("document").textValue()).isEqualTo("randomId");
  }

  @Test
  public void shouldSerializeOrderOfChangeset() throws JsonProcessingException {
    Changeset changeset =
        Changeset.builder().document("randomId").order(12L).fields(Collections.emptyList()).build();
    JsonNode serializedJson = objectMapper.valueToTree(changeset);
    assertThat(serializedJson.get("order").longValue()).isEqualTo(12L);
  }

  @Test
  public void shouldSerializeTimestamp() throws JsonProcessingException {
    Changeset changeset =
        Changeset.builder().document("randomId").order(1L).fields(Collections.emptyList()).build();
    JsonNode serializedJson = objectMapper.valueToTree(changeset);
    assertThat(serializedJson.get("timestamp").asText()).isNotEmpty();
  }

  @Test
  public void shouldSerializeTimestampInISO8601Format()
      throws JsonProcessingException, ParseException {
    Changeset changeset =
        Changeset.builder().document("randomId").order(1L).fields(Collections.emptyList()).build();
    JsonNode serializedJson = objectMapper.valueToTree(changeset);
    DateTime parsed =
        ISODateTimeFormat.dateTime().parseDateTime(serializedJson.get("timestamp").textValue());
    assertThat(changeset.getTimestamp()).isEqualTo(parsed);
  }

  @Test
  public void shouldSerializedOnlyFieldsNotGetterMethods() throws JsonProcessingException {
    Changeset changeset =
        Changeset.builder().document("randomId").order(1L).fields(Collections.emptyList()).build();
    JsonNode serializedJson = objectMapper.valueToTree(changeset);
    List<String> fieldNames = new ArrayList<>();
    serializedJson.fieldNames().forEachRemaining(fieldNames::add);
    assertThat(fieldNames).containsOnly("document", "timestamp", "order", "fields");
  }

  @Test
  public void shouldSerializeAllChangedFields() throws JsonProcessingException {
    List<Fields> changed = new ArrayList<>();
    changed.add(Fields.builder().op("REPLACE").path("/name").value("0").build());
    Changeset changeset =
        Changeset.builder().document("randomId").order(1L).fields(changed).build();

    JsonNode serializedChangedFields = objectMapper.valueToTree(changeset).get("fields");
    assertThat(serializedChangedFields.isArray()).isTrue();

    JsonNode changedNameField = serializedChangedFields.get(0);
    assertThat(changedNameField.get("op").textValue()).isEqualTo("REPLACE");
    assertThat(changedNameField.get("path").textValue()).isEqualTo("/name");
    assertThat(changedNameField.get("value").textValue()).isEqualTo("0");
  }

  @Test
  public void shouldSerializeArraysInChangedValueFieldProperly() throws JsonProcessingException {
    List<Fields> changed = new ArrayList<>();
    changed
        .add(Fields.builder().op("REPLACE").path("/name").value("[\"a\", \"b\", \"c\"]").build());
    Changeset changeset =
        Changeset.builder().document("randomId").order(1L).fields(changed).build();

    JsonNode serializedChangedFields = objectMapper.valueToTree(changeset).get("fields");
    JsonNode changedNameField = serializedChangedFields.get(0);
    assertThat(changedNameField.get("value").textValue()).isEqualTo("[\"a\", \"b\", \"c\"]");
  }

  @Test
  public void shouldSerializeNestedStructuresInChangedValueFieldProperly()
      throws JsonProcessingException {
    List<Fields> changed = new ArrayList<>();
    changed.add(Fields.builder().op("REPLACE").path("/name")
        .value("{ \"test\" : { \"myarray\": [\"a\", \"b\", \"c\"] } }").build());
    Changeset changeset =
        Changeset.builder().document("randomId").order(1L).fields(changed).build();

    JsonNode serializedChangedFields = objectMapper.valueToTree(changeset).get("fields");
    JsonNode changedNameField = serializedChangedFields.get(0);
    assertThat(changedNameField.get("value").textValue())
        .isEqualTo("{ \"test\" : { \"myarray\": [\"a\", \"b\", \"c\"] } }");
  }
}
