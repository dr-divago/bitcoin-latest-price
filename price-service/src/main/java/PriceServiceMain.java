import io.reactivex.disposables.Disposable;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import verticle.HttpPriceServiceVerticle;
import verticle.PriceConsumerVerticle;

public class PriceServiceMain {

  private static final Logger logger = LoggerFactory.getLogger(PriceServiceMain.class);

  public static void main(String... args) {
    Vertx vertx = Vertx.vertx();
    ConfigRetriever retriever = ConfigRetriever.create(vertx);
    retriever.rxGetConfig()
      .doOnSuccess( conf -> initVerticle(vertx, conf))
      .doOnError( error -> logger.error(error.getMessage()))
      .subscribe();
  }

  private static Disposable initVerticle(Vertx vertx, JsonObject conf) {
    return vertx
      .rxDeployVerticle( new PriceConsumerVerticle(), new DeploymentOptions().setConfig(conf))
      .flatMap( id -> vertx.rxDeployVerticle(new HttpPriceServiceVerticle()))
      .subscribe(
        ok -> logger.info("Price Service started on port {}", HttpPriceServiceVerticle.HTTP_PORT),
        error -> logger.error("Error starting Price History Service", error)
      );
  }
}
