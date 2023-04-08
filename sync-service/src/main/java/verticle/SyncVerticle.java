package verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import model.BitcoinData;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class SyncVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(SyncVerticle.class);
  private static final String uri = "https://pro-api.coinmarketcap.com";
  private WebClient webClient;

  @Override
  public void start(Promise<Void> promise)  {

        initVerticle(vertx);
        promise.complete();
  }

  private void initVerticle(Vertx vertx) {
    logger.info("period: " + config().getInteger("period"));

    webClient = WebClient.create(vertx, new WebClientOptions().setLogActivity(true).setSsl(true).setTrustAll(true));
    syncBitcoinPrice();
    vertx.setPeriodic(60000*config().getInteger("period"), x -> syncBitcoinPrice());

  }

  private void syncBitcoinPrice() {
    webClient
      .getAbs(uri + "/v1/cryptocurrency/listings/latest")
      .addQueryParam("start", "1")
      .addQueryParam("limit", "1")
      .addQueryParam("convert", "USD")
      .putHeader(HttpHeaders.ACCEPT.toString(), "application/json")
      .putHeader("X-CMC_PRO_API_KEY", config().getString("apiKey"))
      .as(BodyCodec.jsonObject())
      .send()
      .onSuccess(this::filterJson)
      .onFailure( fail -> logger.error("Error connecting : " + fail));
  }
  private void filterJson(HttpResponse<JsonObject> resp) {
    JsonObject body = resp.body();

    BitcoinData bitcoinData = body.mapTo(BitcoinData.class);
    double price = bitcoinData.data.get(0).quote.uSD.price;

    TimeZone tz = TimeZone.getTimeZone("UTC");
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
    df.setTimeZone(tz);
    String timestamp = df.format(bitcoinData.data.get(0).last_updated);

    JsonObject bitcoinPrice = new JsonObject()
      .put("price", price)
      .put("timestamp", timestamp);
    logger.info("Publish to kafka.bitcoin.price " + bitcoinPrice);
    vertx.eventBus().publish("kafka.bitcoin.price", bitcoinPrice);
  }
}
