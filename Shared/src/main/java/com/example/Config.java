package com.example;

public record Config(String bootstrapServers, String host, int port, String db, String user, String password, String topic, Integer period) {
}
