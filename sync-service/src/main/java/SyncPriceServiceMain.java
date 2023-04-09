
import com.example.ConfigBuilder;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import verticle.KafkaProducerVerticle;
import verticle.SyncVerticle;

public class SyncPriceServiceMain {

    private static final Logger logger = LoggerFactory.getLogger(SyncPriceServiceMain.class);

    public static void main(String... args) {

        Vertx vertx = Vertx.vertx();
        ConfigBuilder configBuilder = new ConfigBuilder(vertx.getDelegate());
        configBuilder.build().onSuccess(config -> {
            logger.info("Config file correctly loaded");
            JsonObject configSyncVerticle = new JsonObject().put("period", config.period()).put("apiKey", config.apiKey());

            vertx
                .rxDeployVerticle(new SyncVerticle(), new DeploymentOptions().setConfig(configSyncVerticle))
                .flatMap(id -> vertx.rxDeployVerticle(new KafkaProducerVerticle(), new DeploymentOptions().setConfig(config.getKafkaConfig().toJsonObject())))
                .subscribe(
                    ok -> logger.info("All Price Services started"),
                    error -> logger.error("Error starting Price Service", error)
                );
        }).onFailure(err -> logger.error("Error reading configuration!", err));

    }
}
