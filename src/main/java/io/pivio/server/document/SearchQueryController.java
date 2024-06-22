package io.pivio.server.document;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;

@CrossOrigin
@RestController
@Log4j2
public class SearchQueryController {

  private final DocumentRepository documentRepository;
  private final ObjectMapper mapper;
  private final FieldFilter fieldFilter;
  private final Counter getDocumentsCallCounter;

  public SearchQueryController(DocumentRepository repository, ObjectMapper mapper,
      FieldFilter fieldFilter, MeterRegistry registry) {
    this.documentRepository = repository;
    this.mapper = mapper;
    this.fieldFilter = fieldFilter;
    this.getDocumentsCallCounter = registry.counter("counter.calls.document.get");
  }

  @GetMapping(value = "/document", produces = MediaType.APPLICATION_JSON_VALUE)
  public ArrayNode search(@RequestParam(required = false) String query,
      @RequestParam(required = false) String fields, @RequestParam(required = false) String sort,
      HttpServletResponse response) throws IOException {

    getDocumentsCallCounter.increment();
    if (!isRequestValid(fields, sort)) {
      log.info("Received search query with invalid parameters, fields: {}, sort: {}", fields, sort);
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return null;
    }

    Page<PivioDocument> searchResponse = null;
    if (query == null || query.isBlank() || query.isEmpty()) {
      searchResponse = documentRepository.findAll(Pageable.ofSize(100));
    } else {
      searchResponse = documentRepository.findByUsingCustomQuery(query, Pageable.ofSize(100));
    }
    List<String> filterForFields = new LinkedList<>();
    if (fields != null && fields.split(",").length > 0) {
      filterForFields.addAll(Arrays.asList(fields.split(",")));
      filterForFields.add("id");
    }

    ArrayNode searchResult = mapper.createArrayNode();
    searchResponse.forEach(searchHit -> {
      JsonNode document = mapper.valueToTree(searchHit);
      if (filterForFields.isEmpty()) {
        searchResult.add(document);
      } else {
        searchResult.add(fieldFilter.filterFields(document, filterForFields));
      }
    });
    return searchResult;
  }

  private boolean isRequestValid(String fields, String sort) {
    if (fields != null && fields.trim().isEmpty()) {
      return false;
    }
    if (sort == null) {
      return true;
    }
    if (sort.trim().isEmpty()) {
      return false;
    }
    for (String sortPair : sort.split(",")) {
      String[] sortPairConfig = sortPair.split(":");
      if (sortPairConfig.length != 2) {
        return false;
      }
      if (!"asc".equalsIgnoreCase(sortPairConfig[1])
          && !"desc".equalsIgnoreCase(sortPairConfig[1])) {
        return false;
      }
    }
    return true;
  }
}
