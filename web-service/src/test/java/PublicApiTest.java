import io.reactivex.Completable;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.kafka.client.producer.KafkaProducer;
import io.vertx.reactivex.kafka.client.producer.KafkaProducerRecord;
import io.vertx.reactivex.pgclient.PgPool;
import io.vertx.reactivex.sqlclient.Tuple;
import io.vertx.sqlclient.PoolOptions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import verticle.KafkaConfig;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Integration Test for public API")
@Testcontainers
public class PublicApiTest {

  @Container
  private static final DockerComposeContainer CONTAINER = new DockerComposeContainer(new File("../docker-compose.yml"));

  private static final Logger logger = LoggerFactory.getLogger(PublicApiTest.class);

  private RequestSpecification requestSpecification;
  private KafkaProducer<String, JsonObject> priceEventProducer;


  @BeforeAll
  public void prepareSpec(Vertx vertx, VertxTestContext testContext) {
    requestSpecification = new RequestSpecBuilder()
      .addFilters(asList(new ResponseLoggingFilter(), new RequestLoggingFilter()))
      .setBaseUri("http://localhost:4000/")
      .setBasePath("/api/v1")
      .build();

    priceEventProducer = KafkaProducer.create(vertx, KafkaConfig.producer());

    String insertQuery = "INSERT INTO bitcoin (price, price_timestamp) VALUES ($1, $2)";
    List<Tuple> data = Arrays.asList(
      Tuple.of(15456.88, OffsetDateTime.of(2019, 6, 15, 10, 3, 0, 0, ZoneOffset.UTC)),
      Tuple.of(16456.99, OffsetDateTime.of(2019, 6, 15, 12, 30, 0, 0, ZoneOffset.UTC)),
      Tuple.of(14467.54, OffsetDateTime.of(2019, 6, 16, 23, 00, 0, 0, ZoneOffset.UTC))
    );
    PgPool pgPool = PgPool.pool(vertx, new PgConnectOptions()
      .setHost("localhost")
      .setDatabase("postgres")
      .setUser("postgres")
      .setPassword("12345678"), new PoolOptions());

    pgPool.preparedQuery(insertQuery)
      .rxExecuteBatch(data)
      .ignoreElement()
      .andThen(vertx.rxDeployVerticle(new PublicApiVerticle()))
      .ignoreElement()
      .andThen(vertx.rxDeployVerticle("bitcoinprice.HttpPriceHistoryServiceVerticle"))
      .ignoreElement()
      .andThen(vertx.rxDeployVerticle("verticle.HttpPriceServiceVerticle"))
      .ignoreElement()
      .andThen(vertx.rxDeployVerticle("verticle.PriceConsumerVerticle"))
      .ignoreElement()
      .andThen(Completable.fromAction(pgPool::close))
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
  @Order(1)
  @DisplayName("Get the latest bitcoin price")
  void getLatestBitcoinPrice(Vertx vertx, VertxTestContext testContext) {
    priceEventProducer
      .rxSend(latestPriceUpdate(14567.23))
      .subscribe(
        ok -> sendRequest(testContext),
        fail -> testContext.failNow(fail)
      );
  }

  private void sendRequest(VertxTestContext testContext) {
    given(requestSpecification)
      .contentType(ContentType.JSON)
      .get("/latest")
      .then()
      .assertThat()
      .statusCode(200);

    testContext.completeNow();
  }


  @Test
  @Order(2)
  @DisplayName("Get bitcoin price from range date")
  void getBitcoinPriceBetweenDates() throws UnsupportedEncodingException {
    String startDate = LocalDate.of(2019, 6, 15).toString();
    String endDate = LocalDate.of(2019, 6, 16).toString();
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

    assertThat(result)
      .isNotNull()
      .isNotBlank();


  }
}
