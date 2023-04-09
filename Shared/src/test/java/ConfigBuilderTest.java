import com.example.Config;
import com.example.ConfigBuilder;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;



@ExtendWith(VertxExtension.class)
public class ConfigBuilderTest {
    Vertx vertx = Vertx.vertx();

    @Test
    public void testKafkaConfig() throws Throwable {
        VertxTestContext testContext = new VertxTestContext();

        ConfigBuilder configBuilder = new ConfigBuilder(vertx);
        Future<Config> f = configBuilder.build().onComplete(testContext.succeedingThenComplete());

        Assertions.assertThat(testContext.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        Assertions.assertThat(f.result().getKafkaConfig().getBootstrapServers()).isEqualTo("localhost:29092");
        Assertions.assertThat(f.result().getKafkaConfig().getTopic()).isEqualTo("bitcoin.price");
        if (testContext.failed()) {
            throw testContext.causeOfFailure();
        }
    }

    @Test
    public void testServiceConfig() throws Throwable {
        VertxTestContext testContext = new VertxTestContext();

        ConfigBuilder configBuilder = new ConfigBuilder(vertx);
        Future<Config> f = configBuilder.build().onComplete(testContext.succeedingThenComplete());

        Assertions.assertThat(testContext.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        f.onComplete(testContext.succeeding(v -> {
            Assertions.assertThat(v.getServiceConfig().getWebServiceConfig().port()).isEqualTo(4000);
            Assertions.assertThat(v.getServiceConfig().getPriceServiceConfig().port()).isEqualTo(5000);
            testContext.completeNow();
        }));
        if (testContext.failed()) {
            throw testContext.causeOfFailure();
        }
    }
}
