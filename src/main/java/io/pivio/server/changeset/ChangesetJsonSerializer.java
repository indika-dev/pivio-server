package io.pivio.server.changeset;

import java.io.IOException;
import java.util.Iterator;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.boot.jackson.JsonObjectSerializer;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.pivio.server.elasticsearch.Changeset;
import io.pivio.server.elasticsearch.Fields;

@JsonComponent
public class ChangesetJsonSerializer extends JsonObjectSerializer<Changeset> {

  @Override
  protected void serializeObject(Changeset changeset, JsonGenerator jgen,
      SerializerProvider provider) throws IOException {
    jgen.writeStringField("document", changeset.getDocument());
    jgen.writeNumberField("order", changeset.getOrder());
    jgen.writeStringField("timestamp",
        ISODateTimeFormat.dateTime().print(changeset.getTimestamp()));
    jgen.writeArrayFieldStart("fields");
    Iterator<Fields> patches = changeset.getFields().iterator();
    while (patches.hasNext()) {
      jgen.writeStartObject();
      Fields current = patches.next();
      jgen.writeStringField("op", current.getOp());
      jgen.writeStringField("path", current.getPath());
      if (current.getValue() != null && !current.getValue().isEmpty()
          && !current.getValue().isBlank()) {
        jgen.writeStringField("value",
            removeLeadingAndTrailingDoubleQuotes(current.getValue().toString()).replace("\\\"",
                "\""));
      }
      jgen.writeEndObject();
    }
    jgen.writeEndArray();
  }

  private String removeLeadingAndTrailingDoubleQuotes(String str) {
    if (str.length() == 0) {
      return str;
    }

    int start = str.charAt(0) == '"' ? 1 : 0;
    int end = str.charAt(str.length() - 1) == '"' ? str.length() - 1 : str.length();
    return str.substring(start, end);
  }
}
