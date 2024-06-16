package io.pivio.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import io.pivio.server.changeset.ChangesetRepository;
import io.pivio.server.document.DocumentRepository;
import io.pivio.server.document.PivioDocument;
import io.pivio.server.elasticsearch.Changeset;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@SpringBootApplication(exclude = {ElasticsearchDataAutoConfiguration.class})
public class AppLauncher {

  @Autowired
  ElasticsearchOperations operations;
  @Autowired
  ChangesetRepository changeSetRepository;
  @Autowired
  DocumentRepository pivioDocumentRepository;

  @PreDestroy
  public void deleteIndex() {
    operations.indexOps(Changeset.class).delete();
    operations.indexOps(PivioDocument.class).delete();
  }

  @PostConstruct
  public void prepareDb() {
    operations.indexOps(Changeset.class).refresh();
    operations.indexOps(PivioDocument.class).refresh();
  }

  public static void main(String[] args) throws Exception {
    SpringApplication.run(AppLauncher.class, args);
  }
}
