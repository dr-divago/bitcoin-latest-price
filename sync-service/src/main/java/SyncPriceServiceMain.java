
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import verticle.SyncVerticle;

public class SyncPriceServiceMain {

  private static final Logger logger = LoggerFactory.getLogger(SyncPriceServiceMain.class);

  public static void main(String... args) {
    Vertx vertx = Vertx.vertx();

    vertx.rxDeployVerticle(new SyncVerticle())
      .subscribe(
        ok -> logger.info("verticle.SyncVerticle running "),
        error -> logger.error("Error starting verticle.SyncVerticle {}", error)
      );

    vertx.rxDeployVerticle(new KafkaProducerVerticle())
      .subscribe(
        ok -> logger.info("KafkaProducerVerticle Service running"),
        error -> logger.error("Error starting KafkaProducerVerticle {}", error)
      );
  }
}
