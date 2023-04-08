package bitcoinprice;

import com.example.ConfigBuilder;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PriceHistoryServiceMain {

  private static final Logger logger = LoggerFactory.getLogger(PriceHistoryServiceMain.class);

  public static void main(String... args) {
      Vertx vertx = Vertx.vertx();
      ConfigBuilder configBuilder = new ConfigBuilder(vertx.getDelegate());
        configBuilder.build().onSuccess(config -> {
            logger.info("Config file correctly loaded");
            JsonObject dbConfig = config.dbConfig().toJsonObject();
            JsonObject httpConfig = config.priceHistoryServiceConfig().toJsonObject();
            JsonObject kafkaConfig = config.getKafkaConfig().toJsonObject();

            DeploymentOptions databaseUpdateVerticleConfig = new DeploymentOptions().setConfig(httpConfig.mergeIn(kafkaConfig));
            DeploymentOptions httpPriceHistoryServiceVerticleConfig = new DeploymentOptions().setConfig(httpConfig.mergeIn(dbConfig));
            vertx
                .rxDeployVerticle(new DatabaseUpdateVerticle(), databaseUpdateVerticleConfig)
                .flatMap( id -> vertx.rxDeployVerticle(new HttpPriceHistoryServiceVerticle(), httpPriceHistoryServiceVerticleConfig))
                .subscribe(
                    ok -> logger.info("Price History Service started"),
                    error -> logger.error("Error starting Price History Service", error)
                );
        }).onFailure(err -> logger.error("Error reading configuration!", err));
  }
}
