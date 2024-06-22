package io.pivio.server.changeset;

import java.io.IOException;
import org.joda.time.DateTime;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.pivio.server.elasticsearch.ElasticsearchQueryHelper;
import lombok.extern.log4j.Log4j2;

@CrossOrigin
@RestController
@Log4j2
public class ChangesetController {

  private final Counter getChangeSetCounter;
  private final Counter docIdChangeSetCounter;
  private final ElasticsearchQueryHelper queryHelper;

  public ChangesetController(MeterRegistry registry, ElasticsearchQueryHelper helper) {
    this.getChangeSetCounter = registry.counter("counter.calls.changeset.get");
    this.docIdChangeSetCounter = registry.counter("counter.calls.document.id.changeset.get");
    this.queryHelper = helper;
  }

  @GetMapping(value = "/changeset", produces = MediaType.APPLICATION_JSON_VALUE)
  public ArrayNode listAll(@RequestParam(required = false) String since) throws IOException {
    getChangeSetCounter.increment();
    if (!isSinceParameterValid(since)) {
      log.info("Received changeset request with invalid since parameter in {} for all documents",
          since);
      throw new InvalidSinceParameterException(since);
    }

    log.debug("Retrieving changesets for all documents with since parameter {}", since);
    return queryHelper.retrieveAllChangesets(createQuery(since));
  }

  @GetMapping(value = "/document/{id}/changeset", produces = MediaType.APPLICATION_JSON_VALUE)
  public ArrayNode get(@PathVariable String id, @RequestParam(required = false) String since)
      throws IOException {
    docIdChangeSetCounter.increment();

    if (queryHelper.isDocumentPresent(id).isEmpty()) {
      log.info("Client wants to retrieve changesets for missing document with id {}", id);
      throw new DocumentNotFoundException(id);
    }

    if (!isSinceParameterValid(since)) {
      log.info("Received changeset request with invalid since parameter in {} for document {}",
          since, id);
      throw new InvalidSinceParameterException(since);
    }

    log.debug("Retrieving changesets for document {} with since parameter {}", id, since);
    return queryHelper.retrieveAllChangesets(createQuery(id, since));
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
