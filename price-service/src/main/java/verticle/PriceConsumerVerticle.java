package verticle;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.RxHelper;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.kafka.client.consumer.KafkaConsumer;
import io.vertx.reactivex.kafka.client.consumer.KafkaConsumerRecord;

import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PriceConsumerVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(PriceConsumerVerticle.class);

    @Override
    public Completable rxStart() {
        ConfigStoreOptions env = new ConfigStoreOptions().setType("env");
        ConfigStoreOptions file = new ConfigStoreOptions().setType("file").setConfig(new JsonObject().put("path", "conf/config.json"));
        ConfigRetriever configRetriever = ConfigRetriever.create(vertx.getDelegate(), new ConfigRetrieverOptions().addStore(file).addStore(env));
        configRetriever.getConfig(ar -> {
            if (ar.failed()) {
                logger.error("Error reading config file");
                Completable.error(ar.cause());
            } else {
                logger.info("Config file correctly loaded");
                String bootstrapServers;
                if (config().containsKey("BOOTSTRAP_SERVERS")) {
                    bootstrapServers = config().getString("BOOTSTRAP_SERVERS");
                }
                else {
                    bootstrapServers = ar.result().getString("BOOTSTRAP_SERVERS");
                }

                String topic = ar.result().getString("KAFKA_BITCOIN_PRICE_TOPIC");

                KafkaConsumer<String, JsonObject> priceEventConsumer =
                    KafkaConsumer.create(vertx, KafkaConfig.consumerConfig("price-service", bootstrapServers));

                priceEventConsumer
                    .subscribe(topic)
                    .toFlowable()
                    .flatMap(this::notifyLatestPrice)
                    .doOnError(err -> logger.error("Error!", err))
                    .retryWhen(this::retryLater)
                    .subscribe();
            }
        });

        return Completable.complete();
    }


    private Publisher<?> retryLater(Flowable<Throwable> errors) {
        return errors.delay(10, TimeUnit.SECONDS, RxHelper.scheduler(vertx.getDelegate()));
    }

    private Flowable<Void> notifyLatestPrice(KafkaConsumerRecord<String, JsonObject> record) {

        logger.debug("Received {} from kafka topic", record.value());
        JsonObject json = record.value();
        double latestPrice = json.getDouble("price");

        vertx.eventBus().publish("bitcoin.price.latest", latestPrice);
        return Flowable.empty();
    }

}
