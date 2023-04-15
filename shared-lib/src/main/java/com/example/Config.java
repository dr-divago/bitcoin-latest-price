package com.example;

public record Config(KafkaConfig kafkaConfig, DbConfig dbConfig, HttpConfig webServiceConfig, HttpConfig priceServiceConfig, HttpConfig priceHistoryServiceConfig,
                     String apiKey,
                     Integer period) {


    public KafkaConfig getKafkaConfig() {
        return kafkaConfig;
    }

    public ServiceConfig getServiceConfig() {
        return new ServiceConfig(priceServiceConfig, webServiceConfig, priceHistoryServiceConfig);
    }
}
