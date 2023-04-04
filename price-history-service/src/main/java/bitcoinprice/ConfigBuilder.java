package bitcoinprice;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;

public class ConfigBuilder {
    private final Vertx vertx;

    public ConfigBuilder(Vertx vertx) {
        this.vertx = vertx;
    }

    public Future<Config> build() {
        ConfigStoreOptions env = new ConfigStoreOptions().setType("env");
        ConfigRetriever configRetriever = ConfigRetriever.create(vertx.getDelegate(), new ConfigRetrieverOptions().addStore(env).setIncludeDefaultStores(true));
        return configRetriever.getConfig()
            .flatMap( config -> Future.succeededFuture(config(config)));
    }

    private Config config(JsonObject config) {
        String bootstrapServers = config.getString("BOOTSTRAP_SERVERS");
        String host = config.getString("HOST");
        Integer port= Integer.parseInt(config.getString("PORT"));
        String dbName = config.getString("DB_NAME");
        String userName = config.getString("POSTGRES_USER");
        String password = config.getString("POSTGRES_PASSWORD");

        return new Config(bootstrapServers, host, port, dbName, userName, password);
    }
}


