package io.pivio.server.elasticsearch;

import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(indexName = "changeset", createIndex = true)
@Setting(settingPath = "settings.json")
public class Changeset {

  @Field(name = "document", type = FieldType.Text)
  private String document;
  @Field(name = "order", type = FieldType.Long)
  private long order;
  @Field(name = "timestamp", type = FieldType.Date)
  private DateTime timestamp;
  @Field(name = "fields", type = FieldType.Nested)
  @Builder.Default
  private List<Fields> fields = new ArrayList<>();

  public boolean isEmpty() {
    return fields == null || fields.isEmpty();
  }
}
