package com.example;

import io.vertx.core.json.JsonObject;

public class KafkaConfig {
    private final String bootstrapServers;
    private final String topic;
    public KafkaConfig(String bootstrapServers, String topic) {
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public String getTopic() {
        return topic;
    }

    public JsonObject toJsonObject() {
        return new JsonObject()
            .put("bootstrapServers", bootstrapServers)
            .put("topic", topic);
    }
}
