package bitcoinprice;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PriceHistoryServiceMain {

  private static final Logger logger = LoggerFactory.getLogger(PriceHistoryServiceMain.class);

  public static void main(String... args) {
    Vertx vertx = Vertx.vertx();
    ConfigRetriever retriever = ConfigRetriever.create(vertx);
    retriever.rxGetConfig()
      .doOnSuccess( conf -> initVerticles(vertx, conf))
      .doOnError( error -> logger.error(error.getMessage()))
      .subscribe();
  }

  private static void initVerticles(Vertx vertx, JsonObject conf) {
    vertx
      .rxDeployVerticle(new DatabaseUpdateVerticle(), new DeploymentOptions().setConfig(conf))
      .flatMap( id -> vertx.rxDeployVerticle(new HttpPriceHistoryServiceVerticle()))
      .subscribe(
        ok -> logger.info("Price History Service started"),
        error -> logger.error("Error starting Price History Service", error)
      );
  }


}
