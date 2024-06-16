package io.pivio.server.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Document(indexName = "steckbrief", createIndex = true)
@Setting(settingPath = "settings.json")
@Mapping(mappingPath = "mapping.json")
public class PivioDocument {

  @Id
  private String id;

  @Field(name = "type", type = FieldType.Text)
  private String type;
  @Field(name = "name", type = FieldType.Text)
  private String name;
  @Field(name = "short_name", type = FieldType.Text)
  private String serviceName;
  @Field(name = "owner", type = FieldType.Text)
  private String owner;
  @Field(name = "description", type = FieldType.Text)
  private String description;
}
