package bitcoinprice;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgException;
import io.vertx.reactivex.RxHelper;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.kafka.client.consumer.KafkaConsumer;
import io.vertx.reactivex.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.reactivex.pgclient.PgPool;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.RowSet;
import io.vertx.reactivex.sqlclient.Tuple;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

public class DatabaseUpdateVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseUpdateVerticle.class);
    private static final String INSERT_PRICE = "INSERT INTO bitcoin (price, price_timestamp) VALUES ($1, $2)";
    private PgPool pgPool;

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
                String bootstrapServers;
                if (config().containsKey("BOOTSTRAP_SERVERS")) {
                    bootstrapServers = config().getString("BOOTSTRAP_SERVERS");
                }
                else {
                    bootstrapServers = ar.result().getString("BOOTSTRAP_SERVERS");
                }
                String host;
                if (config().containsKey("HOST")) {
                    host = config().getString("HOST");
                }
                else {
                    host = ar.result().getString("HOST");
                }
                Integer port;
                if (config().containsKey("PORT")) {
                    port = config().getInteger("PORT");
                }
                else {
                    port = Integer.parseInt(ar.result().getString("PORT"));
                }
                String dbName;
                if (config().containsKey("DB_NAME")) {
                    dbName = config().getString("DB_NAME");
                }
                else {
                    dbName = ar.result().getString("DB_NAME");
                }
                String userName;
                if(config().containsKey("USER_NAME")) {
                    userName = config().getString("USER_NAME");
                }
                else {
                    userName = ar.result().getString("USER_NAME");
                }
                String password;
                if(config().containsKey("PASSWORD")) {
                    password = config().getString("PASSWORD");
                }
                else {
                    password = ar.result().getString("PASSWORD");
                }

                KafkaConsumer<String, JsonObject> eventConsumer = KafkaConsumer.create(vertx, KafkaConfig.consumerConfig("price-service", bootstrapServers));
                pgPool = PgPool.pool(vertx, PgConfig.pgConnectOpts(host, port, dbName, userName, password), new PoolOptions());

                eventConsumer
                    .subscribe("bitcoin.price")
                    .toFlowable()
                    .flatMap(this::insertRecord)
                    .doOnError(err -> logger.error("Error! " + err.getMessage(), err))
                    .retryWhen(this::retryLater)
                    .subscribe();
            }
        });

        return Completable.complete();
    }

    private Flowable<Throwable> retryLater(Flowable<Throwable> errors) {
        return errors.delay(50, TimeUnit.SECONDS, RxHelper.scheduler(vertx.getDelegate()));
    }

    private Flowable<RowSet<Row>> insertRecord(KafkaConsumerRecord<String, JsonObject> record) {
        JsonObject data = record.value();

        OffsetDateTime timestamp = OffsetDateTime.parse(data.getString("timestamp"));

        Tuple values = Tuple.of(
            data.getDouble("price"),
            timestamp
        );

        return pgPool
            .preparedQuery(INSERT_PRICE)
            .rxExecute(values)
            .onErrorReturn(err -> {
                throw new RuntimeException(err);
            })
            .toFlowable();
    }

    private boolean duplicateKeyInsert(Throwable err) {
        return (err instanceof PgException) && "23505".equals(((PgException) err).getCode());
    }
}
