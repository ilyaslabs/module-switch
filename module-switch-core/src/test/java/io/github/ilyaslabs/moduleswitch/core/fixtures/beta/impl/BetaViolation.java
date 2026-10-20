package io.github.ilyaslabs.moduleswitch.core.fixtures.beta.impl;

import java.util.List;

import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.impl.AlphaService;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.impl.deep.AlphaHelper;

/** Uses alpha's implementation directly: a boundary violation. */
public class BetaViolation {

    private AlphaService service;

    public List<AlphaHelper> helpers() {
        return List.of();
    }
}
