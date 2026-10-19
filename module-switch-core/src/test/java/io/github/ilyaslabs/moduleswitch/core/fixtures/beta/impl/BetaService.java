package io.github.ilyaslabs.moduleswitch.core.fixtures.beta.impl;

import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.AlphaApi;
import io.github.ilyaslabs.moduleswitch.core.fixtures.beta.api.BetaApi;

/** Uses alpha only through its API: allowed. */
public class BetaService implements BetaApi {

    private final AlphaApi alpha;

    public BetaService(AlphaApi alpha) {
        this.alpha = alpha;
    }

    @Override
    public String hello(String name) {
        return "hello " + name + " " + this.alpha.count();
    }
}
