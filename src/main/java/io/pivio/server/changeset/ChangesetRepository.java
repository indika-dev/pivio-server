package io.pivio.server.changeset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import io.pivio.server.elasticsearch.Changeset;

/**
 * ChangeSetRepository
 */
public interface ChangesetRepository extends ElasticsearchRepository<Changeset, String> {


  @Query("{\"bool\": {\"must\": [{\"match\": {\"document\": \"?0\"}},{\"range\": {\"timestamp\": { \"gte\": \"?1\",\"lte\": \"now\"}}}]}}")
  Page<Changeset> findByIdAndSinceUsingCustomQuery(String id, String since, Pageable pageable);

  @Query("{\"query\": {\"range\": {\"timestamp\": { \"gte\": \"?0\",\"lte\": \"now\"}}}}")
  Page<Changeset> findBySinceUsingCustomQuery(String since, Pageable pageable);

  @Query("{\"query\": \"?0\"}")
  Page<Changeset> findByUsingCustomQuery(String query, Pageable pageable);

  Page<Changeset> findByDocument(String document, Pageable pageable);

}
