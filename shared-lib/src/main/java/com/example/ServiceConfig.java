package com.example;

public class ServiceConfig {
    private final HttpConfig priceServiceConfig;
    private final HttpConfig webServiceConfig;
    private final HttpConfig priceHistoryServiceConfig;


    public ServiceConfig(HttpConfig priceServiceConfig, HttpConfig webServiceConfig, HttpConfig priceHistoryServiceConfig) {
        this.priceServiceConfig = priceServiceConfig;
        this.webServiceConfig = webServiceConfig;
        this.priceHistoryServiceConfig = priceHistoryServiceConfig;
    }

    public HttpConfig getPriceServiceConfig() {
        return priceServiceConfig;
    }

    public HttpConfig getWebServiceConfig() {
        return webServiceConfig;
    }
    public HttpConfig getPriceHistoryServiceConfig() {
        return priceHistoryServiceConfig;
    }
}
