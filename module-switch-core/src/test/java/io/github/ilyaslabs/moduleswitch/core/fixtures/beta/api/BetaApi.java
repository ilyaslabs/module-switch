package io.github.ilyaslabs.moduleswitch.core.fixtures.beta.api;

import io.github.ilyaslabs.moduleswitch.Param;

public interface BetaApi {

    String hello(@Param("name") String name);
}
