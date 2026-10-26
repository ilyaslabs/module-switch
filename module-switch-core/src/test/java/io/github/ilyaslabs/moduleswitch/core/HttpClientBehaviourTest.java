package io.github.ilyaslabs.moduleswitch.core;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.ilyaslabs.moduleswitch.ModuleException;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.AlphaApi;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The generated client against a scripted server, for retries, timeouts and headers. */
class HttpClientBehaviourTest {

    private HttpServer stub;

    private final AtomicInteger calls = new AtomicInteger();

    private final List<Headers> received = new CopyOnWriteArrayList<>();

    private final List<Duration> sleeps = new ArrayList<>();

    @AfterEach
    void stop() {
        if (this.stub != null) {
            this.stub.stop(0);
        }
    }

    /** Responds with what the script returns for the attempt number (1-based). */
    private String serve(IntFunction<Reply> script) throws IOException {
        this.stub = HttpServer.create(new InetSocketAddress(0), 0);
        this.stub.createContext("/", (exchange) -> {
            int attempt = this.calls.incrementAndGet();
            this.received.add(exchange.getRequestHeaders());
            exchange.getRequestBody().readAllBytes();
            Reply reply = script.apply(attempt);
            if (reply.delayMillis > 0) {
                try {
                    Thread.sleep(reply.delayMillis);
                }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            reply.headers.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            byte[] body = reply.body.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(reply.status, (body.length == 0) ? -1 : body.length);
                if (body.length > 0) {
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                }
            }
            catch (IOException ex) {
                // The client gave up (timeout test).
            }
            exchange.close();
        });
        this.stub.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        this.stub.start();
        return "http://localhost:" + this.stub.getAddress().getPort();
    }

    private AlphaApi client(String url) {
        return client(url, Map.of());
    }

    private AlphaApi client(String url, Map<String, String> incoming) {
        return TestSupport.client(AlphaApi.class, "alpha", TestSupport.settings(url), incoming, this.sleeps,
                ObservationRegistry.NOOP);
    }

    record Reply(int status, String body, Map<String, String> headers, long delayMillis) {

        static Reply of(int status, String body) {
            return new Reply(status, body, Map.of("Content-Type", "application/json"), 0);
        }

        static Reply slow(long delayMillis) {
            return new Reply(200, "42", Map.of(), delayMillis);
        }
    }

    @Test
    void postsJsonToTheOperationPath() throws Exception {
        List<String> paths = new CopyOnWriteArrayList<>();
        this.stub = HttpServer.create(new InetSocketAddress(0), 0);
        this.stub.createContext("/", (HttpExchange exchange) -> {
            paths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8) + " "
                    + exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        this.stub.start();

        client("http://localhost:" + this.stub.getAddress().getPort() + "/").touch(5);

        assertThat(paths).containsExactly("POST /alpha/AlphaApi/touch {\"id\":5} application/json");
    }

    @Test
    void voidMethodsAcceptAny2xx() throws Exception {
        String url = serve((attempt) -> Reply.of(202, "{\"ignored\":true}"));

        client(url).touch(1);

        assertThat(this.calls).hasValue(1);
    }

    @Test
    void status204IsNull() throws Exception {
        String url = serve((attempt) -> Reply.of(204, ""));

        assertThat(client(url).get(1)).isNull();
    }

    @Test
    void connectionRefusedIsRetriedThenUnavailable() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        assertThatThrownBy(() -> client("http://localhost:" + port).touch(1))
            .isInstanceOfSatisfying(ModuleException.Unavailable.class, (ex) -> {
                assertThat(ex.code()).isEqualTo("MODULE_UNAVAILABLE");
                assertThat(ex).hasMessageContaining("is unreachable");
            });
        assertThat(this.sleeps).hasSize(2);
    }

    @Test
    void unknownHostIsRetriedThenUnavailable() {
        assertThatThrownBy(() -> client("http://module-switch-does-not-exist.invalid").touch(1))
            .isInstanceOf(ModuleException.Unavailable.class);
        assertThat(this.sleeps).hasSize(2);
    }

    @Test
    void notReadyIsRetriedEvenForNonIdempotentMethods() throws Exception {
        String url = serve((attempt) -> (attempt < 3)
                ? new Reply(503, "", Map.of(WireFormat.NOT_READY_HEADER, "true"), 0) : Reply.of(204, ""));

        client(url).touch(1);

        assertThat(this.calls).hasValue(3);
    }

    @Test
    void plain503IsNotRetriedForNonIdempotentMethods() throws Exception {
        String url = serve((attempt) -> Reply.of(503, "{\"detail\":\"overloaded\"}"));

        assertThatThrownBy(() -> client(url).touch(1)).isInstanceOf(ModuleException.Unavailable.class)
            .hasMessage("overloaded");
        assertThat(this.calls).hasValue(1);
    }

    @Test
    void gatewayErrorsAreRetriedForIdempotentMethods() throws Exception {
        String url = serve((attempt) -> switch (attempt) {
            case 1 -> Reply.of(502, "");
            case 2 -> Reply.of(504, "");
            default -> Reply.of(200, "{\"id\":1,\"name\":\"a\",\"price\":1}");
        });

        assertThat(client(url).idempotentGet(1).name()).isEqualTo("a");
        assertThat(this.calls).hasValue(3);
    }

    @Test
    void readTimeoutIsNotRetriedForNonIdempotentMethods() throws Exception {
        String url = serve((attempt) -> Reply.slow(1500));

        assertThatThrownBy(() -> client(url).count()).isInstanceOf(ModuleException.Unavailable.class)
            .hasMessageContaining("did not answer within");
        assertThat(this.calls).hasValue(1);
        assertThat(this.sleeps).isEmpty();
    }

    @Test
    void readTimeoutIsRetriedForIdempotentMethods() throws Exception {
        String url = serve((attempt) -> (attempt == 1) ? Reply.slow(1500)
                : Reply.of(200, "{\"id\":1,\"name\":\"a\",\"price\":1}"));

        assertThat(client(url).idempotentGet(1)).isNotNull();
        assertThat(this.calls).hasValue(2);
    }

    @Test
    void moduleExceptionsAreNeverRetried() throws Exception {
        String url = serve((attempt) -> Reply.of(404, "{\"detail\":\"gone\",\"code\":\"GONE\"}"));

        assertThatThrownBy(() -> client(url).idempotentGet(1)).isInstanceOf(ModuleException.NotFound.class);
        assertThat(this.calls).hasValue(1);
    }

    @Test
    void retriesStopAtMaxAttempts() throws Exception {
        String url = serve((attempt) -> new Reply(503, "", Map.of(WireFormat.NOT_READY_HEADER, "true"), 0));
        ModuleSwitchProperties.ModuleProperties settings = TestSupport.settings(url);
        settings.getRetry().setMaxAttempts(5);
        AlphaApi alpha = TestSupport.client(AlphaApi.class, "alpha", settings, Map.of(), this.sleeps,
                ObservationRegistry.NOOP);

        assertThatThrownBy(alpha::count).isInstanceOf(ModuleException.Unavailable.class);
        assertThat(this.calls).hasValue(5);
        assertThat(this.sleeps).hasSize(4);
    }

    @Test
    void retriesCanBeDisabled() throws Exception {
        String url = serve((attempt) -> new Reply(503, "", Map.of(WireFormat.NOT_READY_HEADER, "true"), 0));
        ModuleSwitchProperties.ModuleProperties settings = TestSupport.settings(url);
        settings.getRetry().setMaxAttempts(1);

        assertThatThrownBy(() -> TestSupport.client(AlphaApi.class, "alpha", settings).count())
            .isInstanceOf(ModuleException.Unavailable.class);
        assertThat(this.calls).hasValue(1);
    }

    @Test
    void sendsTokenAndForwardsConfiguredHeadersOnly() throws Exception {
        String url = serve((attempt) -> Reply.of(200, "42"));
        ModuleSwitchProperties.ModuleProperties settings = TestSupport.settings(url);
        settings.setToken("t0ken");
        AlphaApi alpha = TestSupport.client(AlphaApi.class, "alpha", settings,
                Map.of("Authorization", "Bearer xyz", "X-Tenant", "acme", "Cookie", "secret"), this.sleeps,
                ObservationRegistry.NOOP);

        alpha.count();

        Headers headers = this.received.getFirst();
        assertThat(headers.getFirst(WireFormat.TOKEN_HEADER)).isEqualTo("t0ken");
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer xyz");
        assertThat(headers.getFirst("X-Tenant")).isEqualTo("acme");
        assertThat(headers.getFirst("Cookie")).isNull();
    }

    @Test
    void absentHeadersAreNotSent() throws Exception {
        String url = serve((attempt) -> Reply.of(200, "42"));

        client(url).count();

        assertThat(this.received.getFirst().containsKey("Authorization")).isFalse();
        assertThat(this.received.getFirst().containsKey(WireFormat.TOKEN_HEADER)).isFalse();
    }

    @Test
    void htmlErrorPagesBecomeExceptionsByStatus() throws Exception {
        String url = serve((attempt) -> new Reply(403, "<html>nope</html>", Map.of("Content-Type", "text/html"), 0));

        assertThatThrownBy(() -> client(url).count()).isInstanceOf(ModuleException.Forbidden.class)
            .hasMessageContaining("HTTP 403");
    }

    @Test
    void malformedSuccessBodyIsInternal() throws Exception {
        String url = serve((attempt) -> Reply.of(200, "{not json"));

        assertThatThrownBy(() -> client(url).get(1)).isInstanceOf(ModuleException.Internal.class);
    }

    @Test
    void toStringNamesTheRemote() throws Exception {
        String url = serve((attempt) -> Reply.of(200, "42"));

        assertThat(client(url).toString()).contains("remote").contains(url);
        assertThat(this.calls).hasValue(0);
    }
}
