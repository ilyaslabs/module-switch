package io.github.ilyaslabs.moduleswitch.core.fixtures.plain;

import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.impl.AlphaService;

/** Code outside any module that reaches into a module implementation. */
public class PlainConsumer {

    public PlainConsumer(AlphaService service) {
    }
}
