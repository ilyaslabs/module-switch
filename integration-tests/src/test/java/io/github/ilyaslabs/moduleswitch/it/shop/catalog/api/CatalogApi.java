package io.github.ilyaslabs.moduleswitch.it.shop.catalog.api;

import java.util.List;

import io.github.ilyaslabs.moduleswitch.Idempotent;
import io.github.ilyaslabs.moduleswitch.Param;

public interface CatalogApi {

    /** Throws NotFound for id 404 and fails unexpectedly for id 500. */
    Product product(@Param("id") long id);

    List<Product> search(@Param("category") String category);

    /** The Authorization header forwarded to catalog, or "none". */
    String caller();

    @Idempotent
    int stock(@Param("id") long id);
}
