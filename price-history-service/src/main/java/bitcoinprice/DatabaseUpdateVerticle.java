package bitcoinprice;

import io.reactivex.Completable;
import io.reactivex.Flowable;
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
        logger.info("Starting DatabaseUpdateVerticle");
        KafkaConsumer<String, JsonObject> eventConsumer = KafkaConsumer.create(vertx, KafkaConfig.consumerConfig("price-service", config().getString("bootstrapServers")));
        pgPool = PgPool.pool(vertx, PgConfig.pgConnectOpts(
            config().getString("dbHost"),
            Integer.parseInt(config().getString("dbPort")),
            config().getString("dbName"),
            config().getString("userName"),
            config().getString("password")), new PoolOptions());

        eventConsumer
            .subscribe(config().getString("topic"))
            .toFlowable()
            .flatMap(this::insertRecord)
            .doOnError(err -> logger.error("Error! " + err.getMessage(), err))
            .retryWhen(this::retryLater)
            .subscribe();

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
