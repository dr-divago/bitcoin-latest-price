
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncPriceServiceMain {

  private static final Logger logger = LoggerFactory.getLogger(SyncPriceServiceMain.class);

  public static void main(String... args) {

    Vertx vertx = Vertx.vertx();

    vertx.deployVerticle(SyncVerticle.class.getName());
    vertx.deployVerticle(KafkaProducerVerticle.class.getName());
  }
}
