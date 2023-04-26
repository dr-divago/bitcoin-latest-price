import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.client.predicate.ResponsePredicate;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class PublicApiVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(PublicApiVerticle.class);

    private WebClient webClient;

    private final JsonObject config = new JsonObject();

    @Override
    public void start(Promise<Void> promise) {

        logger.info("Config file correctly loaded");
        webClient = WebClient.create(vertx);

        storeConfig(config())
          .compose(this::configureRouter)
          .compose(this::startHttpServer)
          .onSuccess( ok -> promise.complete())
          .onFailure(promise::fail);
    }

    Future<Void> storeConfig(JsonObject other) {
        config.mergeIn(other);
        return Future.succeededFuture();
    }

    private Future<Router> configureRouter(Void unused) {
        Router router = Router.router(vertx.getDelegate());
        Route latest = Route.of("/api/v1/latest");
        Route priceRange = Route.of("/api/v1/priceRange");
        router.get(latest.route()).handler(this::latestPrice);
        router.post().handler(BodyHandler.create());
        router.post(priceRange.route()).handler(this::priceRange);
        return Future.succeededFuture(router);
    }

    Future<HttpServer> startHttpServer(Router router) {
      HttpServer server = vertx.getDelegate().createHttpServer()
        .requestHandler(router);

      int port = Integer.parseInt(config.getString("web.service.port"));
      return server.listen(port);
    }

    private void priceRange(RoutingContext ctx) {
        if (isRequestValid(ctx)) {
            webClient
                .post(Integer.parseInt(config.getString("price_history.service.port")), "price_history.service.host", "/priceRange")
                .putHeader("Content-Type", "application/json")
                .as(BodyCodec.jsonArray())
                .expect(ResponsePredicate.SC_OK)
                .send( req -> ctx.body().asJsonObject());

        } else {
            logger.error("Error request");
            sendStatusCode(ctx, 400);
        }
    }

    private boolean isRequestValid(RoutingContext ctx) {

        JsonObject request = ctx.body().asJsonObject();
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
        } catch (DateTimeParseException e) {
            logger.error(e.getMessage());
            return false;
        }

        return true;
    }

    private void latestPrice(RoutingContext ctx) {
        webClient
            .get(Integer.parseInt(config.getString("price.service.port")), config.getString("price.service.host"), "/latest")
            .as(BodyCodec.jsonObject())
            .expect(ResponsePredicate.SC_SUCCESS)
            .rxSend()
            .retry(5)
            .subscribe(
              resp -> forwardJsonObjectResponse(ctx, resp.getDelegate()),
              err -> handleError(ctx, err)
            );

    }

    private void handleError(RoutingContext ctx, Throwable err) {
        logger.error("Error connection to Price Service", err);
        ctx.fail(503);
    }

    private void forwardJsonObjectResponse(RoutingContext ctx, HttpResponse<JsonObject> resp) {
        if (resp.statusCode() != 200) {
            sendStatusCode(ctx, resp.statusCode());
        } else {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(resp.body().encode());
        }
    }

    private void sendStatusCode(RoutingContext ctx, int statusCode) {
        ctx.response().setStatusCode(statusCode).end();
    }
}
