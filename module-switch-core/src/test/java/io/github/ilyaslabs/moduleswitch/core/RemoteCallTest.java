package io.github.ilyaslabs.moduleswitch.core;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.ilyaslabs.moduleswitch.ModuleException;
import io.github.ilyaslabs.moduleswitch.Param;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.AlphaApi;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.Item;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.impl.AlphaService;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A generated client talking to the internal server, both real. */
class RemoteCallTest {

    private final AlphaService service = new AlphaService();

    private ModuleSwitchServer server;

    private String url;

    @BeforeEach
    void start() {
        this.server = TestSupport.server("alpha", AlphaApi.class, TestSupport.local(AlphaApi.class, "alpha", this.service),
                null);
        this.server.start();
        this.server.setReady(true);
        this.url = "http://localhost:" + this.server.getPort();
    }

    @AfterEach
    void stop() {
        this.server.stop();
    }

    private AlphaApi client() {
        return TestSupport.client(AlphaApi.class, "alpha", TestSupport.settings(this.url));
    }

    @Test
    void returnsResults() {
        AlphaApi alpha = client();

        assertThat(alpha.get(7)).isEqualTo(new Item(7, "item-7", new BigDecimal("9.99")));
        assertThat(alpha.search("shoe", 2)).extracting(Item::name).containsExactly("shoe1", "shoe2");
        assertThat(alpha.grouped(List.of("ab"))).containsOnlyKeys("ab");
        assertThat(alpha.count()).isEqualTo(42);
        assertThat(alpha.echo("héllo ✓")).isEqualTo("héllo ✓");
    }

    @Test
    void nullAndVoidResults() {
        AlphaApi alpha = client();

        assertThat(alpha.get(0)).isNull();
        alpha.touch(1);
        assertThat(this.service.touched).isEqualTo(1);
        assertThat(alpha.echo(null)).isNull();
    }

    @Test
    void defaultMethodsRunOnTheCaller() {
        assertThat(client().greeting()).isEqualTo("hello from default");
    }

    @Test
    void moduleExceptionsArriveAsTheSameType() {
        assertThatThrownBy(() -> client().get(404)).isInstanceOfSatisfying(ModuleException.NotFound.class, (ex) -> {
            assertThat(ex).hasMessage("Item 404 not found");
            assertThat(ex.code()).isEqualTo("ITEM_NOT_FOUND");
            assertThat(ex.details()).containsEntry("id", 404);
        });
    }

    @Test
    void otherExceptionsArriveAsInternal() {
        assertThatThrownBy(() -> client().get(500)).isInstanceOf(ModuleException.Internal.class)
            .hasMessageContaining("boom");
    }

    @Test
    void localAndRemoteCallsFailTheSameWay() {
        AlphaApi local = TestSupport.local(AlphaApi.class, "alpha", this.service);
        Throwable localError = org.assertj.core.api.Assertions.catchThrowable(() -> local.get(500));
        Throwable remoteError = org.assertj.core.api.Assertions.catchThrowable(() -> client().get(500));

        assertThat(remoteError).hasSameClassAs(localError);
        assertThat(((ModuleException) remoteError).status()).isEqualTo(((ModuleException) localError).status());
    }

    @Test
    void notReadyAnswersRetryable503() throws Exception {
        this.server.setReady(false);

        HttpResponse<String> response = post("/alpha/AlphaApi/count", "{}", null);

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue(WireFormat.NOT_READY_HEADER)).hasValue("true");
        assertThat(response.headers().firstValue("Content-Type")).hasValue(WireFormat.PROBLEM_JSON);
    }

    @Test
    void clientRetriesWhileServerIsNotReady() {
        this.server.setReady(false);
        List<java.time.Duration> sleeps = new ArrayList<>();
        AlphaApi alpha = TestSupport.client(AlphaApi.class, "alpha", TestSupport.settings(this.url), Map.of(), sleeps,
                io.micrometer.observation.ObservationRegistry.NOOP);

        assertThatThrownBy(alpha::count).isInstanceOf(ModuleException.Unavailable.class);
        assertThat(sleeps).hasSize(2);
    }

    @Test
    void unknownOperationIs404() throws Exception {
        HttpResponse<String> response = post("/alpha/AlphaApi/nope", "{}", null);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("UNKNOWN_OPERATION");
    }

    @Test
    void onlyPostIsAllowed() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(HttpRequest.newBuilder(URI.create(this.url + "/alpha/AlphaApi/count")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("Allow")).hasValue("POST");
    }

    @Test
    void invalidBodyIs400() throws Exception {
        assertThat(post("/alpha/AlphaApi/get", "{\"id\":\"x\"}", null).statusCode()).isEqualTo(400);
        assertThat(post("/alpha/AlphaApi/get", "not json", null).statusCode()).isEqualTo(400);
    }

    @Test
    void tokenIsRequiredWhenConfigured() throws Exception {
        this.server.stop();
        this.server = TestSupport.server("alpha", AlphaApi.class, TestSupport.local(AlphaApi.class, "alpha", this.service),
                "s3cret");
        this.server.start();
        this.server.setReady(true);
        this.url = "http://localhost:" + this.server.getPort();

        assertThat(post("/alpha/AlphaApi/count", "{}", null).statusCode()).isEqualTo(401);
        assertThat(post("/alpha/AlphaApi/count", "{}", "wrong").statusCode()).isEqualTo(401);
        assertThat(post("/alpha/AlphaApi/count", "{}", "s3cret").statusCode()).isEqualTo(200);

        assertThatThrownBy(() -> client().count()).isInstanceOfSatisfying(ModuleException.Unauthorized.class,
                (ex) -> assertThat(ex.code()).isEqualTo("INVALID_TOKEN"));
        ModuleSwitchProperties.ModuleProperties settings = TestSupport.settings(this.url);
        settings.setToken("s3cret");
        assertThat(TestSupport.client(AlphaApi.class, "alpha", settings).count()).isEqualTo(42);
    }

    @Test
    void forwardedHeadersAreVisibleToTheImplementation() {
        this.server.stop();
        HeaderEcho echo = new HeaderEcho();
        this.server = TestSupport.server("alpha", HeaderEchoApi.class, TestSupport.local(HeaderEchoApi.class, "alpha", echo),
                null);
        this.server.start();
        this.server.setReady(true);
        HeaderEchoApi client = TestSupport.client(HeaderEchoApi.class, "alpha",
                TestSupport.settings("http://localhost:" + this.server.getPort()),
                Map.of("Authorization", "Bearer abc", "X-Other", "not forwarded"), new ArrayList<>(),
                io.micrometer.observation.ObservationRegistry.NOOP);

        assertThat(client.header("authorization")).isEqualTo("Bearer abc");
        assertThat(client.header("X-Other")).isNull();
        assertThat(client.header(WireFormat.TOKEN_HEADER)).isNull();
        assertThat(ModuleCallContext.current()).isEmpty();
    }

    @Test
    void recordsClientObservations() {
        TestObservationRegistry observations = TestObservationRegistry.create();
        AlphaApi alpha = TestSupport.client(AlphaApi.class, "alpha", TestSupport.settings(this.url), Map.of(),
                new ArrayList<>(), observations);

        alpha.count();
        assertThatThrownBy(() -> alpha.get(404));

        TestObservationRegistryAssert.assertThat(observations)
            .hasNumberOfObservationsWithNameEqualTo(CallObservations.NAME, 2)
            .hasAnObservationWithAKeyValue("mode", "http")
            .hasAnObservationWithAKeyValue("outcome", "success")
            .hasAnObservationWithAKeyValue("outcome", "NotFound");
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(this.url + path))
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header(WireFormat.TOKEN_HEADER, token);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    public interface HeaderEchoApi {

        String header(@Param("name") String name);
    }

    static class HeaderEcho implements HeaderEchoApi {

        @Override
        public String header(String name) {
            return ModuleCallContext.current().flatMap((context) -> context.header(name)).orElse(null);
        }
    }
}
