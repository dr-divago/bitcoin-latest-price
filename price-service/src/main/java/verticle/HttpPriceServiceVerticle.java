package verticle;

import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

public class HttpPriceServiceVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(HttpPriceServiceVerticle.class);
  public static final int HTTP_PORT = 5000;
  private final AtomicReference<Double> latestPrice = new AtomicReference<>();

  @Override
  public Completable rxStart() {

    asyncUpdatePrice();

    Router router = Router.router(vertx);
    router.get("/latest").handler(this::latestPrice);

    return vertx.createHttpServer()
      .requestHandler(router)
      .rxListen(HTTP_PORT)
      .ignoreElement();
  }

  private void latestPrice(RoutingContext ctx) {
    JsonObject payload = new JsonObject();
    payload.put("price", latestPrice);

    ctx.response()
      .putHeader("Content-Type", "application/json")
      .end(payload.encode());
  }

  private void asyncUpdatePrice() {
    vertx.eventBus().<Double>consumer("bitcoin.price.latest", handler -> {
      latestPrice.getAndSet(handler.body());
      logger.debug("Received latest price " + latestPrice.get());
    });
  }
}
