import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
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
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import verticle.HttpPriceServiceVerticle;
import verticle.KafkaConfig;
import verticle.PriceConsumerVerticle;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Testcontainers
@DisplayName("Tests for the events-stats service")
public class PriceServiceTest {

  @Container
  private static final DockerComposeContainer CONTAINERS = new DockerComposeContainer(new File("src/test/docker/docker-compose.yml"));

  private KafkaProducer<String, JsonObject> producer;
  private KafkaConsumer<String, JsonObject> consumer;

  private RequestSpecification requestSpecification;

  @BeforeEach
  void prepare(Vertx vertx, VertxTestContext testContext) {
    requestSpecification = new RequestSpecBuilder()
      .addFilters(asList(new ResponseLoggingFilter(), new RequestLoggingFilter()))
      .setBaseUri("http://localhost:5000/")
      .build();

    producer = KafkaProducer.create(vertx, KafkaConfig.producer());
    consumer = KafkaConsumer.create(vertx, KafkaConfig.consumerConfig(UUID.randomUUID().toString()));
    KafkaAdminClient adminClient = KafkaAdminClient.create(vertx, KafkaConfig.producer());
    adminClient
      .rxDeleteTopics(Arrays.asList("bitcoin.price"))
      .onErrorComplete()
      .andThen(vertx.rxDeployVerticle(new PriceConsumerVerticle()))
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
