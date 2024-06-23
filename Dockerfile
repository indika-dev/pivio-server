FROM docker.io/alpine/java:21.0.3-jdk

EXPOSE 8080

ADD target/pivio-server-0.0.1-SNAPSHOT.jar /pivio-server.jar

CMD ["java", "-jar", "/pivio-server.jar", "--spring.data.elasticsearch.cluster-nodes=elasticsearch:9300"]
