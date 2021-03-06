package verticle;

import io.reactivex.Completable;
import io.reactivex.Flowable;
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
  private KafkaConsumer<String, JsonObject> priceEventConsumer;

  @Override
  public Completable rxStart() {
    priceEventConsumer = KafkaConsumer.create(vertx, KafkaConfig.consumerConfig("price-service"));
    priceEventConsumer
      .subscribe("bitcoin.price")
      .toFlowable()
      .flatMap(this::notifyLatestPrice)
      .doOnError( err -> logger.error("Error!", err))
      .retryWhen(this::retryLater)
      .subscribe();

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
