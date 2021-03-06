import bitcoinprice.DatabaseUpdateVerticle;
import bitcoinprice.HttpPriceHistoryServiceVerticle;
import bitcoinprice.KafkaConfig;
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

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Testcontainers
@DisplayName("Tests for the events-stats service")
public class PriceHistoryServiceTest {

  @Container
  private static final DockerComposeContainer CONTAINERS = new DockerComposeContainer(new File("../docker-compose.yml"));

  private KafkaProducer<String, JsonObject> producer;
  private KafkaConsumer<String, JsonObject> consumer;

  private RequestSpecification requestSpecification;

  @BeforeEach
  void prepare(Vertx vertx, VertxTestContext testContext) {
    requestSpecification = new RequestSpecBuilder()
      .addFilters(asList(new ResponseLoggingFilter(), new RequestLoggingFilter()))
      .setBaseUri("http://localhost:6000/")
      .build();

    producer = KafkaProducer.create(vertx, KafkaConfig.producer());
    consumer = KafkaConsumer.create(vertx, KafkaConfig.consumerConfig(UUID.randomUUID().toString()));
    KafkaAdminClient adminClient = KafkaAdminClient.create(vertx, KafkaConfig.producer());
    adminClient
      .rxDeleteTopics(Arrays.asList("bitcoin.price"))
      .onErrorComplete()
      .andThen(vertx.rxDeployVerticle(new DatabaseUpdateVerticle()))
      .ignoreElement()
      .andThen(vertx.rxDeployVerticle(new HttpPriceHistoryServiceVerticle()))
      .ignoreElement()
      .subscribe(testContext::completeNow, testContext::failNow);
  }

  private KafkaProducerRecord<String, JsonObject> latestPriceUpdate(double price) {
    TimeZone tz = TimeZone.getTimeZone("UTC");
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
    df.setTimeZone(tz);
    String timestamp = df.format(new Date());
    String key = price + ":" + LocalDateTime.now().getYear() + "-" + LocalDateTime.now().getMonth() + "-" + LocalDateTime.now().getDayOfMonth();
    JsonObject json = new JsonObject()
      .put("price", price)
      .put("timestamp", timestamp);
    return KafkaProducerRecord.create("bitcoin.price", key, json);
  }

  @Test
  @DisplayName("Bitcoin price between dates")
  void updateBitcoinPriceFromKafka() {
    producer.send(latestPriceUpdate(14567.23));
    producer.send(latestPriceUpdate(15665.25));
    String startDate = LocalDate.now().toString();
    String endDate = LocalDate.now().plusDays(1).toString();

    JsonObject date = new JsonObject()
      .put("start-date", startDate)
      .put("end-date", endDate);

    String result = given(requestSpecification)
      .contentType(ContentType.JSON)
      .body(date.encode())
      .post("/priceRange")
      .then()
      .assertThat()
      .statusCode(200)
      .extract()
      .asString();

    String expectedResult = "\"price\":14567.23";

    assertThat(result)
      .isNotNull()
      .isNotBlank()
      .contains(expectedResult);
  }
}
