import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.client.predicate.ResponsePredicate;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class PublicApiVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(PublicApiVerticle.class);
  public static final int HTTP_PORT = 4000;

  private WebClient webClient;

  @Override
  public Completable rxStart() {

    webClient = WebClient.create(vertx);

    Router router = Router.router(vertx);
    initRoute(router);

    return vertx.createHttpServer()
      .requestHandler(router)
      .rxListen(HTTP_PORT)
      .ignoreElement();
  }

  private void initRoute(Router router) {
    String prefix = "/api/v1";
    router.get(prefix + "/latest").handler(this::latestPrice);
    router.post().handler(BodyHandler.create());
    router.post(prefix + "/priceRange").handler(this::priceRange);
  }

  private void priceRange(RoutingContext ctx) {
    if (isRequestValid(ctx)) {
      Single<HttpResponse<JsonArray>> single = webClient
        .post(6000, "localhost", "/priceRange")
        .putHeader("Content-Type", "application/json")
        .as(BodyCodec.jsonArray())
        .expect(ResponsePredicate.SC_OK)
        .rxSendJsonObject(ctx.getBodyAsJson());

      single.subscribe(
        resp -> forwardJsonArrayResponse(ctx, resp),
        err -> handleError(ctx, err)
      );
    }
    else {
      logger.error("Error request");
      sendStatusCode(ctx,400);
    }
  }

  private boolean isRequestValid(RoutingContext ctx) {

    JsonObject request = ctx.getBodyAsJson();
    String startDate = request.getString("start-date");
    String endDate = request.getString("end-date");

    logger.debug(startDate);
    logger.debug(endDate);

    if (startDate == null || startDate.isEmpty() || endDate == null || endDate.isEmpty()) {
      logger.error(String.valueOf(startDate.length()));
      logger.error(String.valueOf(startDate.length()));
      return false;
    }

    try {
      LocalDate.parse(startDate);
      LocalDate.parse(endDate);
    }catch(DateTimeParseException e) {
      logger.error(e.getMessage());
      return false;
    }

    return true;
  }

  private void latestPrice(RoutingContext ctx) {
    Single<HttpResponse<JsonObject>> single = webClient
      .get(5000, "localhost", "/latest")
      .as(BodyCodec.jsonObject())
      .expect(ResponsePredicate.SC_SUCCESS)
      .rxSend()
      .retry(5);

    single.subscribe(
      resp -> forwardJsonObjectResponse(ctx, resp),
      err -> handleError(ctx, err)
    );

  }

  private void handleError(RoutingContext ctx, Throwable err) {
    logger.error("Error connection to Price Service", err);
    ctx.fail(503);
  }

  private void forwardJsonArrayResponse(RoutingContext ctx, HttpResponse<JsonArray> resp) {
    if (resp.statusCode() != 200) {
      sendStatusCode(ctx, resp.statusCode());
    }
    else {
      ctx.response()
        .putHeader("Content-Type", "application/json")
        .end(resp.body().encode());
    }
  }

  private void forwardJsonObjectResponse(RoutingContext ctx, HttpResponse<JsonObject> resp) {
    if (resp.statusCode() != 200) {
      sendStatusCode(ctx, resp.statusCode());
    }
    else {
      ctx.response()
        .putHeader("Content-Type", "application/json")
        .end(resp.body().encode());
    }
  }

  private void sendStatusCode(RoutingContext ctx, int statusCode) {
    ctx.response().setStatusCode(statusCode).end();
  }
}
