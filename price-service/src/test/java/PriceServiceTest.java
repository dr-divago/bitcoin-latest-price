import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.kafka.admin.KafkaAdminClient;
import io.vertx.reactivex.kafka.client.consumer.KafkaConsumer;
import io.vertx.reactivex.kafka.client.producer.KafkaProducer;
import io.vertx.reactivex.kafka.client.producer.KafkaProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import verticle.HttpPriceServiceVerticle;
import verticle.KafkaConfig;
import verticle.PriceConsumerVerticle;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Testcontainers
@DisplayName("Tests for the events-stats service")
class PriceServiceTest {

  @Container
  private KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.1"));


  private KafkaProducer<String, JsonObject> producer;
  private KafkaConsumer<String, JsonObject> consumer;

  private RequestSpecification requestSpecification;

  @BeforeEach
  void prepare(Vertx vertx, VertxTestContext testContext) {
    requestSpecification = new RequestSpecBuilder()
      .addFilters(asList(new ResponseLoggingFilter(), new RequestLoggingFilter()))
      .setBaseUri("http://localhost:5000/")
      .build();

    JsonObject conf = new JsonObject().put("kafka_bootstrap_server", kafka.getBootstrapServers());

    producer = KafkaProducer.create(vertx, KafkaConfig.producer(kafka.getBootstrapServers()));
    KafkaAdminClient adminClient = KafkaAdminClient.create(vertx, KafkaConfig.producer(kafka.getBootstrapServers()));
    adminClient
      .rxDeleteTopics(List.of("bitcoin.price"))
      .onErrorComplete()
      .andThen(vertx.rxDeployVerticle(new PriceConsumerVerticle(), new DeploymentOptions().setConfig(conf)))
      .ignoreElement()
      .andThen(vertx.rxDeployVerticle(new HttpPriceServiceVerticle()))
      .ignoreElement()
      .subscribe(testContext::completeNow, testContext::failNow);
  }

  private KafkaProducerRecord<String, JsonObject> latestPriceUpdate(double price) {
    LocalDateTime now = LocalDateTime.now();
    String key = price + ":" + now.getYear() + "-" + now.getMonth() + "-" + now.getDayOfMonth();
    JsonObject json = new JsonObject()
      .put("price", price)
      .put("timestamp", now.toString());
    return KafkaProducerRecord.create("bitcoin.price", key, json);
  }

  @Test
  @DisplayName("Update latest price")
  void updateBitcoinPriceFromKafka() {
    producer.send(latestPriceUpdate(14567.23));
    String result = given(requestSpecification)
      .contentType(ContentType.JSON)
      .get("/latest")
      .then()
      .assertThat()
      .statusCode(200)
      .extract()
      .asString();

    assertThat(result)
      .isNotNull()
      .isNotBlank()
      .contains("{\"price\":14567.23}");
  }

  @Test
  @DisplayName("Get latest price without update")
  void getLatestPrice() {
    String result = given(requestSpecification)
      .contentType(ContentType.JSON)
      .get("/latest")
      .then()
      .assertThat()
      .statusCode(200)
      .extract()
      .asString();

    assertThat(result)
      .isNotNull()
      .isNotBlank()
      .contains("{\"price\":0.0}");
  }
}
