package io.github.ilyaslabs.moduleswitch.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.ilyaslabs.moduleswitch.ModuleException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.RequestReplyReceiverContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.SmartLifecycle;

/**
 * The internal HTTP server that makes local modules callable from other deployments.
 * <p>
 * It runs on its own port with its own JDK HTTP server and virtual threads, separate from the
 * application's web server, so none of the application's web filters or security apply. The
 * only check is the optional shared token. While the application is starting or shutting down it
 * answers 503 with {@value WireFormat#NOT_READY_HEADER}, before invoking anything, so callers
 * know the request never ran and can retry safely.
 */
public class ModuleSwitchServer implements SmartLifecycle {

    private static final Log logger = LogFactory.getLog(ModuleSwitchServer.class);

    /** An exposed method and the local proxy that serves it. */
    public record Operation(String module, Class<?> api, Method method, Object target) {
    }

    private final int requestedPort;

    private final String token;

    private final Map<String, Operation> operations;

    private final Supplier<WireFormat> wire;

    private final Supplier<ObservationRegistry> observations;

    private volatile HttpServer server;

    private volatile ExecutorService executor;

    private volatile boolean ready;

    public ModuleSwitchServer(int port, String token, List<Operation> operations, Supplier<WireFormat> wire,
            Supplier<ObservationRegistry> observations) {
        this.requestedPort = port;
        this.token = token;
        Map<String, Operation> byPath = new HashMap<>();
        for (Operation operation : operations) {
            byPath.put(WireFormat.path(operation.module(), operation.api(), operation.method()), operation);
        }
        this.operations = Map.copyOf(byPath);
        this.wire = wire;
        this.observations = observations;
    }

    @Override
    public void start() {
        try {
            this.executor = Executors.newVirtualThreadPerTaskExecutor();
            HttpServer created = HttpServer.create(new InetSocketAddress(this.requestedPort), 0);
            created.setExecutor(this.executor);
            created.createContext("/", this::handle);
            created.start();
            this.server = created;
            logger.info("Module-Switch internal server listening on port " + getPort() + " for "
                    + this.operations.size() + " operation(s)");
        }
        catch (IOException ex) {
            throw new IllegalStateException("Module-Switch could not start its internal server on port "
                    + this.requestedPort, ex);
        }
    }

    @Override
    public void stop() {
        this.ready = false;
        HttpServer current = this.server;
        if (current != null) {
            current.stop(1);
            this.server = null;
        }
        if (this.executor != null) {
            this.executor.close();
            this.executor = null;
        }
    }

    @Override
    public boolean isRunning() {
        return this.server != null;
    }

    /** Starts after the rest of the application, and stops before it. */
    @Override
    public int getPhase() {
        return SmartLifecycle.DEFAULT_PHASE - 1024;
    }

    /** Marks the server ready to invoke methods; until then it answers 503 not-ready. */
    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public boolean isReady() {
        return this.ready;
    }

    /** The port actually bound, useful when configured as 0. */
    public int getPort() {
        HttpServer current = this.server;
        return (current != null) ? current.getAddress().getPort() : -1;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange; InputStream in = exchange.getRequestBody()) {
            byte[] body = in.readAllBytes();
            if (!this.ready) {
                exchange.getResponseHeaders().add(WireFormat.NOT_READY_HEADER, "true");
                sendProblem(exchange, new ModuleException.Unavailable("Application is starting or shutting down.",
                        "NOT_READY"));
                return;
            }
            if (!tokenMatches(exchange.getRequestHeaders().getFirst(WireFormat.TOKEN_HEADER))) {
                sendProblem(exchange, new ModuleException.Unauthorized("Missing or wrong " + WireFormat.TOKEN_HEADER + ".",
                        "INVALID_TOKEN"));
                return;
            }
            Operation operation = this.operations.get(exchange.getRequestURI().getPath());
            if (operation == null) {
                sendProblem(exchange, new ModuleException.NotFound("No exposed module operation at "
                        + exchange.getRequestURI().getPath(), "UNKNOWN_OPERATION"));
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Allow", "POST");
                send(exchange, 405, null, new byte[0]);
                return;
            }
            invoke(exchange, operation, body);
        }
        catch (RuntimeException ex) {
            logger.error("Module-Switch internal server failed to handle " + exchange.getRequestURI(), ex);
        }
    }

    private void invoke(HttpExchange exchange, Operation operation, byte[] body) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!values.isEmpty() && !name.equalsIgnoreCase(WireFormat.TOKEN_HEADER)) {
                headers.put(name, values.getFirst());
            }
        });
        RequestReplyReceiverContext<HttpExchange, byte[]> context = new RequestReplyReceiverContext<>(
                (carrier, key) -> carrier.getRequestHeaders().getFirst(key));
        context.setCarrier(exchange);
        context.setRemoteServiceName(operation.module());
        Observation observation = Observation
            .createNotStarted(CallObservations.NAME + ".server", () -> context, this.observations.get())
            .contextualName(CallObservations.contextualName(operation.api(), operation.method()))
            .lowCardinalityKeyValues(CallObservations.tags(operation.module(), operation.api(), operation.method(),
                    ModuleMode.LOCAL))
            .start();
        ModuleException failure = null;
        Object result = null;
        try (Observation.Scope scope = observation.openScope()) {
            Object[] args = this.wire.get().readArguments(operation.method(), body);
            result = ModuleCallContext.runWith(new ModuleCallContext(operation.module(), headers),
                    () -> call(operation, args));
        }
        catch (ModuleException ex) {
            failure = ex;
        }
        catch (RuntimeException ex) {
            failure = new ModuleException.Internal("Module '" + operation.module() + "' failed: " + ex, null, null, ex);
        }
        finally {
            CallObservations.outcome(observation, failure);
            observation.stop();
        }
        if (failure != null) {
            if (failure instanceof ModuleException.Internal) {
                logger.warn("Module-Switch: " + WireFormat.path(operation.module(), operation.api(), operation.method())
                        + " failed", failure);
            }
            sendProblem(exchange, failure);
        }
        else if (operation.method().getReturnType() == void.class || result == null) {
            send(exchange, 204, null, null);
        }
        else {
            send(exchange, 200, WireFormat.JSON, this.wire.get().writeResult(result));
        }
    }

    private static Object call(Operation operation, Object[] args) {
        try {
            return operation.method().invoke(operation.target(), args);
        }
        catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (ex.getCause() instanceof Error error) {
                throw error;
            }
            throw new ModuleException.Internal("Module '" + operation.module() + "' failed: " + ex.getCause(), null,
                    null, ex.getCause());
        }
        catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private boolean tokenMatches(String presented) {
        if (this.token == null || this.token.isEmpty()) {
            return true;
        }
        return presented != null && MessageDigest.isEqual(this.token.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private void sendProblem(HttpExchange exchange, ModuleException exception) throws IOException {
        send(exchange, exception.status(), WireFormat.PROBLEM_JSON, this.wire.get().writeProblem(exception));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        if (contentType != null) {
            exchange.getResponseHeaders().add("Content-Type", contentType);
        }
        if (body == null || body.length == 0) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
