package bitcoinprice;

import io.vertx.pgclient.PgConnectOptions;

class PgConfig {

  private PgConfig() {}

  public static PgConnectOptions pgConnectOpts(String host, int port, String db, String user, String password) {
    return new PgConnectOptions()
      .setHost(host)
      .setPort(port)
      .setDatabase(db)
      .setUser(user)
      .setPassword(password);
  }
}
