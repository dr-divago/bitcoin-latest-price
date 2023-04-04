package verticle;

import com.example.Config;
import com.example.ConfigBuilder;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.RxHelper;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.kafka.client.consumer.KafkaConsumer;
import io.vertx.reactivex.kafka.client.consumer.KafkaConsumerRecord;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class PriceConsumerVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(PriceConsumerVerticle.class);

    @Override
    public Completable rxStart() {
        ConfigBuilder configBuilder = new ConfigBuilder(vertx.getDelegate());

        Future<Config> future = configBuilder.build().onSuccess(config -> {
                logger.info("Config file correctly loaded");

                KafkaConsumer<String, JsonObject> priceEventConsumer =
                    KafkaConsumer.create(vertx, KafkaConfig.consumerConfig("price-service", config.bootstrapServers()));

                priceEventConsumer
                    .subscribe(config.topic())
                    .toFlowable()
                    .flatMap(this::notifyLatestPrice)
                    .doOnError(err -> logger.error("Error!", err))
                    .retryWhen(this::retryLater)
                    .subscribe();
            }
        ).onFailure(err -> logger.error("Error!", err));

        if (future.failed()) {
            return Completable.error(future.cause());
        }
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
