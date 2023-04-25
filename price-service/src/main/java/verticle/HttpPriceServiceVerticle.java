package verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

public class HttpPriceServiceVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(HttpPriceServiceVerticle.class);
    private final AtomicReference<Double> latestPrice = new AtomicReference<>();

    @Override
    public void start(Promise<Void> startPromise) {
        latestPrice.set(0.0);
        asyncUpdatePrice();

        Router router = Router.router(vertx);
        router.get("/latest").handler(this::latestPrice);

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(Integer.parseInt(config().getString("price.service.port")), result -> {
                if (result.succeeded()) {
                    logger.info("HttpPriceServiceVerticle on port " + config().getString("price.service.port"));
                    startPromise.complete();
                } else {
                    logger.error("Error starting HttpPriceServiceVerticle", result.cause());
                    startPromise.fail(result.cause());
                }
            });
    }

    private void latestPrice(RoutingContext ctx) {
        JsonObject payload = new JsonObject();
        payload.put("price", latestPrice.get());

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
