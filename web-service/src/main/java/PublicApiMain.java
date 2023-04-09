
import com.example.ConfigBuilder;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PublicApiMain {

  private static final Logger logger = LoggerFactory.getLogger(PublicApiMain.class);

  public static void main(String... args) {
    Vertx vertx = Vertx.vertx();


      ConfigBuilder configBuilder = new ConfigBuilder(vertx);
        configBuilder.build().onSuccess(config -> {
            logger.info("Config file correctly loaded");
            config.getServiceConfig().getWebServiceConfig().toJsonObject();
            vertx.deployVerticle(new PublicApiVerticle())
                .onSuccess(ok -> logger.info("Public Api Service running"))
                .onFailure(error -> logger.error("Error starting PublicApi {}", error));
        }).onFailure(err -> logger.error("Error reading configuration!", err));


  }
}
