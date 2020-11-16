package bitcoinprice;

import io.vertx.pgclient.PgConnectOptions;

class PgConfig {

  public static PgConnectOptions pgConnectOpts() {
    return new PgConnectOptions()
      .setHost("localhost")
      .setDatabase("postgres")
      .setUser("postgres")
      .setPassword("12345678");
  }
}
