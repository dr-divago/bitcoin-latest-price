import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bitcoinprice.HttpPriceHistoryServiceVerticle;
import io.reactivex.Completable;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
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
import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import verticle.HttpPriceServiceVerticle;
import verticle.KafkaConfig;
import verticle.PriceConsumerNotifierVerticle;




@ExtendWith(VertxExtension.class)
@Testcontainers
@DisplayName("Integration Test for public API")
class PublicApiTest {

  @Container
  private KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.3")).waitingFor(new WaitAllStrategy());

  @Container
  private PostgreSQLContainer postgreSQLContainer = (PostgreSQLContainer) new PostgreSQLContainer()
    .withDatabaseName("postgres")
    .withUsername("postgres")
    .withPassword("12345678")
    .withInitScript("postgres/setup.sql");

  private static final Logger logger = LoggerFactory.getLogger(PublicApiTest.class);

  private RequestSpecification requestSpecification;
  private KafkaProducer<String, JsonObject> priceEventProducer;


  @BeforeEach
  public void prepareSpec(Vertx vertx, VertxTestContext testContext) {
    assertTrue(kafka.isRunning());
    requestSpecification = new RequestSpecBuilder()
      .addFilters(asList(new ResponseLoggingFilter(), new RequestLoggingFilter()))
      .setBaseUri("http://localhost:4000/")
      .setBasePath("/api/v1")
      .build();

    priceEventProducer = KafkaProducer.create(vertx, KafkaConfig.producer(kafka.getBootstrapServers()));

    String insertQuery = "INSERT INTO bitcoin (price, price_timestamp) VALUES ($1, $2)";
    List<Tuple> data = Arrays.asList(
      Tuple.of(15456.88, OffsetDateTime.of(2019, 6, 15, 10, 3, 0, 0, ZoneOffset.UTC)),
      Tuple.of(16456.99, OffsetDateTime.of(2019, 6, 15, 12, 30, 0, 0, ZoneOffset.UTC)),
      Tuple.of(14467.54, OffsetDateTime.of(2019, 6, 16, 23, 00, 0, 0, ZoneOffset.UTC))
    );

    int port = (int) postgreSQLContainer.getExposedPorts().get(0);
    JsonObject conf = new JsonObject()
      .put("kafka_bootstrap_server", kafka.getBootstrapServers())
      .put("host", postgreSQLContainer.getHost())
      .put("db_name", postgreSQLContainer.getDatabaseName())
      .put("userName", postgreSQLContainer.getUsername())
      .put("password", postgreSQLContainer.getPassword())
      .put("port", postgreSQLContainer.getMappedPort(port));

    PgPool pgPool = PgPool.pool(vertx, new PgConnectOptions()
      .setHost(postgreSQLContainer.getHost())
      .setPort(postgreSQLContainer.getMappedPort(port))
      .setDatabase(postgreSQLContainer.getDatabaseName())
      .setUser(postgreSQLContainer.getUsername())
      .setPassword(postgreSQLContainer.getPassword()), new PoolOptions());

    pgPool
      .preparedQuery(insertQuery)
      .rxExecuteBatch(data)
      .ignoreElement()
      .andThen(vertx.rxDeployVerticle(new PublicApiVerticle(), new DeploymentOptions().setConfig(conf)))
      .ignoreElement()
      .andThen(vertx.rxDeployVerticle(new HttpPriceHistoryServiceVerticle(), new DeploymentOptions().setConfig(conf)))
      .ignoreElement()
      .andThen(vertx.rxDeployVerticle(new HttpPriceServiceVerticle(), new DeploymentOptions().setConfig(conf)))
      .ignoreElement()
      .andThen(vertx.rxDeployVerticle(new PriceConsumerNotifierVerticle(), new DeploymentOptions().setConfig(conf)))
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
  @Disabled
  void getLatestBitcoinPrice(Vertx vertx, VertxTestContext testContext) {
    priceEventProducer
      .rxSend(latestPriceUpdate(14567.23))
      .subscribe(
        ok -> sendRequest(testContext),
          testContext::failNow
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
  @Disabled
  void getBitcoinPriceBetweenDates() {
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
