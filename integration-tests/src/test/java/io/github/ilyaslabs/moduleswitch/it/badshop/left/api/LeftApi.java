package io.github.ilyaslabs.moduleswitch.it.badshop.left.api;

import io.github.ilyaslabs.moduleswitch.Param;

public interface LeftApi {

    String find(@Param("id") long id);

    String find(@Param("sku") String sku);

    String unnamed(String value);
}
