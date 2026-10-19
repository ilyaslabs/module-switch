package io.github.ilyaslabs.moduleswitch.core;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;
import tools.jackson.databind.json.JsonMapper;

/** Builds clients and servers the way {@link ModuleSwitchRuntime} does, without a Spring context. */
final class TestSupport {

    static final WireFormat WIRE = new WireFormat(JsonMapper.builder().build());

    private TestSupport() {
    }

    static ModuleSwitchProperties.ModuleProperties settings(String url) {
        ModuleSwitchProperties.ModuleProperties settings = new ModuleSwitchProperties.ModuleProperties();
        settings.setMode(ModuleMode.HTTP);
        settings.setUrl(URI.create(url));
        settings.setConnectTimeout(Duration.ofMillis(500));
        settings.setReadTimeout(Duration.ofMillis(500));
        settings.getRetry().setInitialBackoff(Duration.ofMillis(1));
        return settings;
    }

    static <T> T client(Class<T> api, String module, ModuleSwitchProperties.ModuleProperties settings,
            Map<String, String> incomingHeaders, List<Duration> sleeps, ObservationRegistry observations) {
        HeaderSource headers = (name) -> java.util.Optional.ofNullable(incomingHeaders.get(name));
        HttpModuleInvocationHandler handler = new HttpModuleInvocationHandler(module, api, settings,
                List.of("Authorization", "X-Tenant"), HttpModuleInvocationHandler.newClient(settings), () -> WIRE,
                () -> observations, headers, sleeps::add);
        return api.cast(Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] { api }, handler));
    }

    static <T> T client(Class<T> api, String module, ModuleSwitchProperties.ModuleProperties settings) {
        return client(api, module, settings, Map.of(), new ArrayList<>(), ObservationRegistry.NOOP);
    }

    static <T> T local(Class<T> api, String module, Object target) {
        return api.cast(Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] { api },
                new LocalModuleInvocationHandler(module, api, () -> target, () -> ObservationRegistry.NOOP)));
    }

    static ModuleSwitchServer server(String module, Class<?> api, Object localProxy, String token) {
        List<ModuleSwitchServer.Operation> operations = new ArrayList<>();
        ApiContract.operations(api)
            .forEach((method) -> operations.add(new ModuleSwitchServer.Operation(module, api, method, localProxy)));
        return new ModuleSwitchServer(0, token, operations, () -> WIRE, () -> ObservationRegistry.NOOP);
    }
}
