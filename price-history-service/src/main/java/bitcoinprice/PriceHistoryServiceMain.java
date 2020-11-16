package bitcoinprice;

import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PriceHistoryServiceMain {

  private static final Logger logger = LoggerFactory.getLogger(PriceHistoryServiceMain.class);

  public static void main(String... args) {
    Vertx vertx = Vertx.vertx();

    vertx
      .rxDeployVerticle(new DatabaseUpdateVerticle())
      .flatMap( id -> vertx.rxDeployVerticle(new HttpPriceHistoryServiceVerticle()))
      .subscribe(
        ok -> logger.info("Price History Service started"),
        error -> logger.error("Error starting Price History Service", error)
      );
  }
}
