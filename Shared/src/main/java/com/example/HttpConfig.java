package com.example;

import io.vertx.core.json.JsonObject;

public record HttpConfig(String prefix, String host, int port) {

    public JsonObject toJsonObject() {
        return new JsonObject()
            .put("host", prefix+"."+host)
            .put("port", port);
    }
}
