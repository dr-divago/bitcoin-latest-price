import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import verticle.HttpPriceServiceVerticle;
import verticle.PriceConsumerVerticle;

public class PriceServiceMain {

  private static final Logger logger = LoggerFactory.getLogger(PriceServiceMain.class);

  public static void main(String... args) {
    Vertx vertx = Vertx.vertx();
    vertx
      .rxDeployVerticle( new PriceConsumerVerticle())
      .flatMap( id -> vertx.rxDeployVerticle(new HttpPriceServiceVerticle()))
      .subscribe(
        ok -> logger.info("Price Service started on port {}", HttpPriceServiceVerticle.HTTP_PORT),
        error -> logger.error("Error starting Price History Service", error)
      );
  }
}
