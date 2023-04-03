package bitcoinprice;

import io.reactivex.Completable;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.pgclient.PgPool;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.RowSet;
import io.vertx.reactivex.sqlclient.Tuple;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class HttpPriceHistoryServiceVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(HttpPriceHistoryServiceVerticle.class);
  public static final int HTTP_PORT = 6000;

  private PgPool pgPool;

  @Override
  public Completable rxStart() {

      ConfigStoreOptions env = new ConfigStoreOptions().setType("env");
      ConfigStoreOptions file = new ConfigStoreOptions().setType("file").setConfig(new JsonObject().put("path", "conf/config.json"));
      ConfigRetriever configRetriever = ConfigRetriever.create(vertx.getDelegate(), new ConfigRetrieverOptions().addStore(file).addStore(env));

      configRetriever.getConfig(ar -> {
          if (ar.failed()) {
              logger.error("Error reading config file");
              Completable.error(ar.cause());
          } else {
              logger.info("Config file correctly loaded");
              String host = ar.result().getString("host");
              Integer port = ar.result().getInteger("port");
              String dbName = ar.result().getString("db_name");
              String userName = ar.result().getString("userName");
              String password = ar.result().getString("password");
              pgPool = PgPool.pool(vertx, PgConfig.pgConnectOpts(host, port, dbName, userName, password), new PoolOptions());
          }
      });

    Router router = Router.router(vertx);
    BodyHandler bodyHandler = BodyHandler.create();
    router.post().handler(bodyHandler);
    router.post("/priceRange").handler(this::priceRange);

    return vertx.createHttpServer()
      .requestHandler(router)
      .rxListen(HTTP_PORT)
      .ignoreElement();
  }

  private void priceRange(RoutingContext ctx) {
    logger.debug("priceRange");

    JsonObject request = ctx.getBodyAsJson();

    String startDateParam = request.getString("start-date");
    logger.debug(startDateParam);
    String endDateParam = request.getString("end-date");
    logger.debug(endDateParam);

    LocalDateTime startDateJson = LocalDate.parse(startDateParam).atStartOfDay();
    LocalDateTime endDateJson = LocalDate.parse(endDateParam).atStartOfDay();

    OffsetDateTime startDate = OffsetDateTime.of(startDateJson, ZoneOffset.UTC);
    OffsetDateTime endDate = OffsetDateTime.of(endDateJson, ZoneOffset.UTC);

    Tuple values = Tuple.of(
      startDate,
      endDate
    );

    pgPool
      .preparedQuery(priceRangeQuery())
      .rxExecute(values)
      .subscribe(
        rows -> forwardResponse(ctx, rows),
        System.out::println
      );
  }

  private void forwardResponse(RoutingContext ctx, RowSet<Row> rows) {
    logger.debug("forwardResponse");
    JsonArray response = new JsonArray();
    for (Row row: rows) {
      JsonObject obs = new JsonObject();
      obs.put("price", row.getDouble(0));
      obs.put("timestamp", row.getOffsetDateTime(1).toString());
      response.add(obs);
    }

    logger.debug(response.encode());
    ctx.response()
      .putHeader("Content-Type", "application/json")
      .end(response.encode());
  }


  private String priceRangeQuery() {
    return "SELECT price, price_timestamp FROM bitcoin WHERE price_timestamp >= $1 and price_timestamp < $2";
  }

}
