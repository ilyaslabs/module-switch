package io.github.ilyaslabs.moduleswitch.core;

import java.lang.reflect.Proxy;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.observation.ObservationRegistry;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Everything Module-Switch resolved at startup: the configuration, the discovered modules, and the
 * proxies callers receive for module APIs.
 */
public class ModuleSwitchRuntime implements SmartInitializingSingleton {

    public static final String BEAN_NAME = "moduleSwitchRuntime";

    private static final String API_BEAN_PREFIX = "moduleSwitch.api.";

    private final ModuleSwitchProperties properties;

    private final ModuleCatalog catalog;

    private final ListableBeanFactory beanFactory;

    private final Map<Class<?>, Object> localTargets = new ConcurrentHashMap<>();

    private final Map<String, HttpClient> httpClients = new ConcurrentHashMap<>();

    private volatile WireFormat wireFormat;

    public ModuleSwitchRuntime(ModuleSwitchProperties properties, ModuleCatalog catalog,
            ListableBeanFactory beanFactory) {
        this.properties = properties;
        this.catalog = catalog;
        this.beanFactory = beanFactory;
    }

    public ModuleSwitchProperties properties() {
        return this.properties;
    }

    public ModuleCatalog catalog() {
        return this.catalog;
    }

    public ModuleMode modeOf(String module) {
        return this.properties.modeOf(module);
    }

    /** The name of the bean callers inject for an API interface. */
    public static String apiBeanName(Class<?> api) {
        return API_BEAN_PREFIX + api.getName();
    }

    /** Creates the proxy for an API interface of a local or http module. */
    public Object createProxy(String module, Class<?> api) {
        ModuleMode mode = modeOf(module);
        Object proxy = switch (mode) {
            case LOCAL -> Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] { api },
                    new LocalModuleInvocationHandler(module, api, () -> localTarget(api), this::observationRegistry));
            case HTTP -> {
                ModuleSwitchProperties.ModuleProperties settings = this.properties.module(module);
                HttpClient client = this.httpClients.computeIfAbsent(module,
                        (key) -> HttpModuleInvocationHandler.newClient(settings));
                yield Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] { api },
                        new HttpModuleInvocationHandler(module, api, settings, this.properties.getPropagateHeaders(),
                                client, this::wireFormat, this::observationRegistry, HeaderSource.standard(),
                                (duration) -> Thread.sleep(duration)));
            }
            case OFF -> throw new IllegalStateException("Module '" + module + "' is off");
        };
        return proxy;
    }

    /** Checks that every API of every local module has exactly one implementation bean. */
    @Override
    public void afterSingletonsInstantiated() {
        List<String> problems = new ArrayList<>();
        for (ModuleCatalog.ModuleInfo module : this.catalog.modules()) {
            if (modeOf(module.name()) != ModuleMode.LOCAL) {
                continue;
            }
            for (Class<?> api : module.apiInterfaces()) {
                List<String> candidates = implementationBeanNames(api);
                if (candidates.isEmpty()) {
                    problems.add("Module '" + module.name() + "' is local but no bean implements " + api.getName()
                            + ". Add an implementation in the module's @Module packages, or set the module to http.");
                }
                else if (candidates.size() > 1) {
                    problems.add("Module '" + module.name() + "' has " + candidates.size() + " beans implementing "
                            + api.getName() + " " + candidates + ". Keep exactly one.");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new ModuleSwitchConfigurationException(problems);
        }
    }

    private Object localTarget(Class<?> api) {
        return this.localTargets.computeIfAbsent(api, (key) -> {
            List<String> candidates = implementationBeanNames(key);
            if (candidates.size() != 1) {
                throw new IllegalStateException("Expected exactly one implementation of " + key.getName()
                        + " but found " + candidates);
            }
            return this.beanFactory.getBean(candidates.getFirst());
        });
    }

    private List<String> implementationBeanNames(Class<?> api) {
        String own = apiBeanName(api);
        return Arrays.stream(this.beanFactory.getBeanNamesForType(api, true, false))
            .filter((name) -> !name.equals(own))
            .toList();
    }

    WireFormat wireFormat() {
        WireFormat current = this.wireFormat;
        if (current == null) {
            JsonMapper mapper = this.beanFactory.getBeanProvider(JsonMapper.class)
                .getIfAvailable(() -> JsonMapper.builder().build());
            current = new WireFormat(mapper);
            this.wireFormat = current;
        }
        return current;
    }

    ObservationRegistry observationRegistry() {
        return this.beanFactory.getBeanProvider(ObservationRegistry.class).getIfUnique(() -> ObservationRegistry.NOOP);
    }

    /** The operations of the modules listed under {@code module-switch.expose.modules}. */
    public List<ModuleSwitchServer.Operation> exposedOperations() {
        List<ModuleSwitchServer.Operation> operations = new ArrayList<>();
        for (String module : this.properties.getExpose().getModules()) {
            for (Class<?> api : this.catalog.apiInterfaces(module)) {
                Object target = this.beanFactory.getBean(apiBeanName(api));
                ApiContract.operations(api)
                    .forEach((method) -> operations.add(new ModuleSwitchServer.Operation(module, api, method, target)));
            }
        }
        return operations;
    }

    /** Builds the internal server for {@code module-switch.expose}. */
    public ModuleSwitchServer createServer() {
        ModuleSwitchProperties.Expose expose = this.properties.getExpose();
        return new ModuleSwitchServer(expose.getPort(), expose.getToken(), exposedOperations(), this::wireFormat,
                this::observationRegistry);
    }
}
