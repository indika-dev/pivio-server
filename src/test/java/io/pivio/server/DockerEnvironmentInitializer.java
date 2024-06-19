package io.pivio.server;

import java.io.File;
import org.assertj.core.util.Files;
import org.junit.ClassRule;
import org.junit.runner.Description;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.DockerComposeContainer;

public class DockerEnvironmentInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final String ELASTICSEARCH_SERVICE_NAME = "elasticsearch_1";
  private static final int ELASTICSEARCH_SERVICE_PORT = 9300;

  private static final Description DOES_NOT_MATTER = null;

  @ClassRule
  public static DockerComposeContainer<?> dockerEnvironment =
      new DockerComposeContainer<>(new File("docker-compose.yml")).withLocalCompose(true)
          .withExposedService(ELASTICSEARCH_SERVICE_NAME, ELASTICSEARCH_SERVICE_PORT)
          .withTailChildContainers(true);

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    buildMainSourcesWhenRunningTestsWithoutGradle();
    setSpringDataElasticsearchClusterNodesProperty(applicationContext, dockerEnvironment);
  }

  private void buildMainSourcesWhenRunningTestsWithoutGradle() {
    if (runningWithoutGradle()) {
      try {
        new ProcessBuilder("./gradlew", "build", "-x", "test").directory(Files.currentFolder())
            .inheritIO().start().waitFor();
      } catch (Exception e) {
        throw new RuntimeException("could not build main sources prior to running tests", e);
      }
    }
  }

  private boolean runningWithoutGradle() {
    // Property is set in build.gradle for all Test tasks
    return System.getProperty("gradleIsRunning") == null;
  }


  private void setSpringDataElasticsearchClusterNodesProperty(
      ConfigurableApplicationContext applicationContext, DockerComposeContainer dockerEnvironment) {
    Integer elasticsearchAmbassadorPort =
        dockerEnvironment.getServicePort(ELASTICSEARCH_SERVICE_NAME, ELASTICSEARCH_SERVICE_PORT);
    EnvironmentTestUtils.addEnvironment(applicationContext,
        "spring.data.elasticsearch.cluster-nodes=localhost:" + elasticsearchAmbassadorPort);
  }
}
