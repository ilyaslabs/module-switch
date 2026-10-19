package io.github.ilyaslabs.moduleswitch.core.badapi;

import io.github.ilyaslabs.moduleswitch.Param;

public interface BadApi {

    String find(@Param("id") long id);

    String find(@Param("sku") String sku);

    String missing(String name);

    String blank(@Param(" ") String name);

    String twice(@Param("a") String first, @Param("a") String second);
}
