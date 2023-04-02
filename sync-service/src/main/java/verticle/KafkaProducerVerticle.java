package verticle;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class KafkaProducerVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(KafkaProducerVerticle.class);
  @Override
  public void start(Promise<Void> promise)  {
    ConfigStoreOptions env = new ConfigStoreOptions().setType("env");

    ConfigRetriever configRetriever = ConfigRetriever.create(vertx, new ConfigRetrieverOptions().addStore(env));
    configRetriever.getConfig( ar -> {
      if (ar.failed()) {
        logger.error("Error reading config file");
        promise.fail(ar.cause());
      }
      else {
        logger.info("Config file correctly loaded");
        String path = ar.result().getString("BOOTSTRAP_SERVER");
        initVerticle(vertx, ar.result());
        promise.complete();
      }
    });
  }

  private void initVerticle(Vertx vertx, JsonObject result) {
    String bootstrapServers = result.getString("kafka_bootstrap_server");
    String topic = result.getString("kafka_bitcoin_price_topic");

    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", bootstrapServers );
    config.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    config.put("value.serializer", "io.vertx.kafka.client.serialization.JsonObjectSerializer");
    config.put("acks", "1");
    KafkaProducer<String, JsonObject> producer = KafkaProducer.create(vertx, config);
    receiveMsgFromEventBusAndSendToKafka(producer, topic);
  }

  private void receiveMsgFromEventBusAndSendToKafka(KafkaProducer<String, JsonObject> producer, String topic){
    vertx.eventBus().<JsonObject>consumer("kafka.bitcoin.price", message -> {
      logger.info("receiving data from event bus {}", "kafka.bitcoin.price");
      produceToKafka(producer, topic, message.body());
    });
  }

  private void produceToKafka(KafkaProducer<String, JsonObject> producer, String topic, JsonObject message) {
    KafkaProducerRecord<String, JsonObject> record = KafkaProducerRecord.create(topic, message);
    producer.send(record, handler -> {
      if (handler.succeeded()) {
        RecordMetadata recordMetadata = handler.result();
        logger.debug("Message " + record.value() + " written on topic=" + recordMetadata.getTopic() +
          ", partition=" + recordMetadata.getPartition() +
          ", offset=" + recordMetadata.getOffset());
      } else if(handler.failed()) {
        logger.error("error receive record from kafka: {}", handler.cause());
      }
    });
  }
}
