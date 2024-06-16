package io.pivio.server.elasticsearch;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import lombok.Builder;
import lombok.Data;

/**
 * Fields
 */
@Data
@Builder
public class Fields {

  @Field(name = "path", type = FieldType.Text, analyzer = "simple")
  private String path;
  @Field(name = "value", type = FieldType.Text)
  private String value;
  @Field(name = "op", type = FieldType.Text)
  private String op;

}
