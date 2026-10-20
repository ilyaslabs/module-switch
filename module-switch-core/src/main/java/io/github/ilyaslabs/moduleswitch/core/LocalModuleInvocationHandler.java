package io.github.ilyaslabs.moduleswitch.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import io.github.ilyaslabs.moduleswitch.ModuleException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * Calls a module's own implementation in-process and gives callers exactly what they would see
 * over HTTP: {@link ModuleException}s pass through, anything else becomes
 * {@link ModuleException.Internal}.
 */
final class LocalModuleInvocationHandler implements InvocationHandler {

    private final String module;

    private final Class<?> api;

    private final Supplier<Object> target;

    private final Supplier<ObservationRegistry> observations;

    LocalModuleInvocationHandler(String module, Class<?> api, Supplier<Object> target,
            Supplier<ObservationRegistry> observations) {
        this.module = module;
        this.api = api;
        this.target = target;
        this.observations = observations;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return ObjectMethods.handle(proxy, method, args, "local " + this.api.getName());
        }
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        Observation observation = Observation
            .createNotStarted(CallObservations.NAME, this.observations.get())
            .contextualName(CallObservations.contextualName(this.api, method))
            .lowCardinalityKeyValues(CallObservations.tags(this.module, this.api, method, ModuleMode.LOCAL))
            .start();
        Throwable failure = null;
        try (Observation.Scope scope = observation.openScope()) {
            return method.invoke(this.target.get(), args);
        }
        catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Error error) {
                failure = error;
                throw error;
            }
            failure = translate(ex.getCause());
            throw failure;
        }
        catch (RuntimeException ex) {
            failure = translate(ex);
            throw failure;
        }
        finally {
            CallObservations.outcome(observation, failure);
            observation.stop();
        }
    }

    private ModuleException translate(Throwable error) {
        if (error instanceof ModuleException moduleException) {
            return moduleException;
        }
        return new ModuleException.Internal("Module '" + this.module + "' failed: " + error, null, null, error);
    }
}
