package com.example;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpConfigTest {

  @Test
  void toJsonObject() {
    HttpConfig httpConfig = new HttpConfig("web.service", "localhost", 4000);
    JsonObject jsonObject =httpConfig.toJsonObject();
    Assertions.assertEquals("{\"web.service.host\":\"localhost\",\"web.service.port\":4000}", jsonObject.encode());
  }

}
