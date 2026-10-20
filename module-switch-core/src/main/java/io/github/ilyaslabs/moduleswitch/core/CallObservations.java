package io.github.ilyaslabs.moduleswitch.core;

import java.lang.reflect.Method;

import io.github.ilyaslabs.moduleswitch.ModuleException;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;

/**
 * Names and tags of the {@value #NAME} observation recorded for every module call.
 */
public final class CallObservations {

    /** Observation (metric and span) name. */
    public static final String NAME = "module.switch.call";

    private CallObservations() {
    }

    static KeyValues tags(String module, Class<?> api, Method method, ModuleMode mode) {
        return KeyValues.of("module", module, "interface", api.getSimpleName(), "method", method.getName(), "mode",
                mode.name().toLowerCase(java.util.Locale.ROOT));
    }

    static void outcome(Observation observation, Throwable error) {
        String outcome;
        if (error == null) {
            outcome = "success";
        }
        else if (error instanceof ModuleException moduleException) {
            outcome = moduleException.getClass().getSimpleName();
        }
        else {
            outcome = "Internal";
        }
        observation.lowCardinalityKeyValue("outcome", outcome);
        if (error != null) {
            observation.error(error);
        }
    }

    static String contextualName(Class<?> api, Method method) {
        return api.getSimpleName() + "." + method.getName();
    }
}
