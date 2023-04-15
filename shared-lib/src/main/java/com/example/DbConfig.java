package com.example;

import io.vertx.core.json.JsonObject;

public class DbConfig {

    private final String dbHost;
    private final Integer dbPort;
    private final String dbName;
    private final String userName;
    private final String password;
    public DbConfig(String dbHost, Integer dbPort, String dbName, String userName, String password) {
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.userName = userName;
        this.password = password;
    }

    public String getDbHost() {
        return dbHost;
    }

    public Integer getDbPort() {
        return dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public JsonObject toJsonObject() {
        return new JsonObject()
            .put("dbHost", dbHost)
            .put("dbPort", dbPort)
            .put("dbName", dbName)
            .put("userName", userName)
            .put("password", password);
    }
}
