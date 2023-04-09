import com.example.ConfigBuilder;
import io.vertx.core.DeploymentOptions;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import verticle.HttpPriceServiceVerticle;
import verticle.PriceConsumerNotifierVerticle;

public class PriceServiceMain {

    private static final Logger logger = LoggerFactory.getLogger(PriceServiceMain.class);

    public static void main(String... args) {
        logger.info("Starting Price Service");
        Vertx vertx = Vertx.vertx();
        ConfigBuilder configBuilder = new ConfigBuilder(vertx.getDelegate());
        configBuilder.build().onSuccess(config -> {
            logger.info("Config file correctly loaded");
            DeploymentOptions priceServiceOptions = new DeploymentOptions().setConfig(config.priceServiceConfig().toJsonObject());
            DeploymentOptions notifierOptions = new DeploymentOptions().setConfig(config.getKafkaConfig().toJsonObject());
            vertx
                .rxDeployVerticle(new PriceConsumerNotifierVerticle(), notifierOptions)
                .flatMap(id -> vertx.rxDeployVerticle(new HttpPriceServiceVerticle(), priceServiceOptions))
                .subscribe(
                    ok -> logger.info("All Price Services started"),
                    error -> logger.error("Error starting Price Service", error)
                );
        }).onFailure(err -> logger.error("Error reading configuration!", err));
    }
}
