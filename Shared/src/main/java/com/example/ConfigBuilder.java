package com.example;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class ConfigBuilder {
    private final Vertx vertx;

    public ConfigBuilder(Vertx vertx) {
        this.vertx = vertx;
    }

    public Future<Config> build() {
        ConfigStoreOptions file = new ConfigStoreOptions().setType("file").setConfig(new JsonObject().put("path", "config.json"));
        ConfigStoreOptions env = new ConfigStoreOptions().setType("env");
        ConfigRetriever configRetriever = ConfigRetriever.create(vertx, new ConfigRetrieverOptions().addStore(file).addStore(env).setIncludeDefaultStores(true));
        return configRetriever.getConfig()
            .flatMap( config -> Future.succeededFuture(config(config)));
    }

    private Config config(JsonObject config) {
        KafkaConfig kafkaConfig = kafkaConfig(config.getString("BOOTSTRAP_SERVERS"), config.getString("TOPIC"));

        String dbHost = config.getString("POSTGRES_HOST");
        String dbName = config.getString("POSTGRES_DB");
        Integer dbPort = config.getString("POSTGRES_PORT") == null ? 0 : Integer.parseInt(config.getString("POSTGRES_PORT"));
        String userName = config.getString("POSTGRES_USER");
        String password = config.getString("POSTGRES_PASSWORD");
        DbConfig dbConfig = new DbConfig(dbHost, dbPort, dbName, userName, password);


        Integer period = config.getString("PERIOD") == null ? 0 : Integer.parseInt(config.getString("PERIOD"));
        String apiKey = config.getString("API_KEY");

        HttpConfig webServiceConfig = httpConfig("web.service", config.getString("WEB_SERVICE_HOST"), config.getString("WEB_SERVICE_PORT") == null ? 0 : Integer.parseInt(config.getString("WEB_SERVICE_PORT")));
        HttpConfig priceServiceConfig = httpConfig("price.service", config.getString("PRICE_SERVICE_HOST"),  config.getString("PRICE_SERVICE_PORT") == null ? 0 : Integer.parseInt(config.getString("PRICE_SERVICE_PORT")));
        HttpConfig syncServiceConfig = httpConfig("sync.service", config.getString("SYNC_SERVICE_HOST"),  config.getString("SYNC_SERVICE_PORT") == null ? 0 : Integer.parseInt(config.getString("SYNC_SERVICE_PORT")));
        HttpConfig priceHistoryServiceConfig = httpConfig("price_history.service", config.getString("PRICE_HISTORY_SERVICE_HOST"),  config.getString("PRICE_HISTORY_SERVICE_PORT") == null ? 0 : Integer.parseInt(config.getString("PRICE_HISTORY_SERVICE_PORT")));


        return new Config(kafkaConfig, dbConfig, webServiceConfig, priceServiceConfig, priceHistoryServiceConfig, apiKey, period);
    }

    private HttpConfig httpConfig(String prefix, String host, Integer port) {
        return new HttpConfig(prefix, host, port);
    }

    private KafkaConfig kafkaConfig(String bootstrapServers, String topic) {
        return new KafkaConfig(bootstrapServers, topic);
    }
}


