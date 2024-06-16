package io.pivio.server.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * DocumentRepository
 */
public interface DocumentRepository extends ElasticsearchRepository<PivioDocument, String> {

  @Query("{\"query\": \"?0\"}")
  Page<PivioDocument> findByUsingCustomQuery(String query, Pageable pageable);
}
