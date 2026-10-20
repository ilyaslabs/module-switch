package io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api;

import java.util.List;
import java.util.Map;

import io.github.ilyaslabs.moduleswitch.Idempotent;
import io.github.ilyaslabs.moduleswitch.Param;

public interface AlphaApi {

    Item get(@Param("id") long id);

    List<Item> search(@Param("query") String query, @Param("limit") int limit);

    Map<String, List<Item>> grouped(@Param("keys") List<String> keys);

    void touch(@Param("id") long id);

    int count();

    @Idempotent
    Item idempotentGet(@Param("id") long id);

    String echo(@Param("text") String text);

    default String greeting() {
        return "hello from default";
    }
}
