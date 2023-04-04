package bitcoinprice;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PriceHistoryServiceMain {

  private static final Logger logger = LoggerFactory.getLogger(PriceHistoryServiceMain.class);

  public static void main(String... args) {
    Vertx vertx = Vertx.vertx();
    initVerticles(vertx);
  }

  private static void initVerticles(Vertx vertx) {
      DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("HELLO_TEST", "TEST"));
    vertx
      .rxDeployVerticle(new DatabaseUpdateVerticle(), options)
      .flatMap( id -> vertx.rxDeployVerticle(new HttpPriceHistoryServiceVerticle()))
      .subscribe(
        ok -> logger.info("Price History Service started"),
        error -> logger.error("Error starting Price History Service", error)
      );
  }


}
