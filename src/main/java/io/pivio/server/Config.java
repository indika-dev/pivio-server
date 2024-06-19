package io.pivio.server;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.spring.boot.autoconfigure.RestClientBuilderCustomizer;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import io.pivio.server.changeset.ChangesetRepository;
import io.pivio.server.document.DocumentRepository;

@SpringBootConfiguration
@EnableElasticsearchRepositories(
    basePackageClasses = {DocumentRepository.class, ChangesetRepository.class})
@ComponentScan(basePackageClasses = {Config.class})
public class Config {

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
  RestClientBuilderCustomizer customizer() {
    return new RestClientBuilderCustomizer() {
      @Override
      public void customize(HttpAsyncClientBuilder builder) {
        try {
          builder.setSSLContext(new SSLContextBuilder()
              .loadTrustMaterial(null, new TrustSelfSignedStrategy()).build());
        } catch (final KeyManagementException | NoSuchAlgorithmException | KeyStoreException ex) {
          throw new RuntimeException("Failed to initialize SSL Context instance", ex);
        }
      }

      @Override
      public void customize(RestClientBuilder builder) {
        // No additional customizations needed
      }
    };
  }

  // @Bean
  // ClientConfiguration clientConfiguration() {
  // HttpHeaders httpHeaders = new HttpHeaders();
  // httpHeaders.add("some-header", "on every request");
  // String username="";
  // String password="";
  //
  // return ClientConfiguration.builder().connectedTo("localhost:9200", "localhost:9291").usingSsl()
  // .withProxy("localhost:8888").withPathPrefix("ela").withConnectTimeout(Duration.ofSeconds(5))
  // .withSocketTimeout(Duration.ofSeconds(3)).withDefaultHeaders(httpHeaders)
  // .withBasicAuth(username, password).withHeaders(() -> {
  // HttpHeaders headers = new HttpHeaders();
  // headers.add("currentTime",
  // LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
  // return headers;
  // }).withClientConfigurer(ElasticsearchHttpClientConfigurationCallback.from(clientBuilder -> {
  // return clientBuilder;
  // })).build();
  // }
  //
}
