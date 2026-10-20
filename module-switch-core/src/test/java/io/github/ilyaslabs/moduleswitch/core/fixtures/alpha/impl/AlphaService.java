package io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import io.github.ilyaslabs.moduleswitch.ModuleException;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.AlphaApi;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.Item;

public class AlphaService implements AlphaApi {

    public int touched;

    @Override
    public Item get(long id) {
        if (id == 404) {
            throw new ModuleException.NotFound("Item " + id + " not found", "ITEM_NOT_FOUND", Map.of("id", id));
        }
        if (id == 500) {
            throw new IllegalStateException("boom");
        }
        if (id == 0) {
            return null;
        }
        return new Item(id, "item-" + id, new BigDecimal("9.99"));
    }

    @Override
    public List<Item> search(String query, int limit) {
        return java.util.stream.LongStream.rangeClosed(1, limit).mapToObj((i) -> new Item(i, query + i, BigDecimal.ONE)).toList();
    }

    @Override
    public Map<String, List<Item>> grouped(List<String> keys) {
        Map<String, List<Item>> result = new java.util.LinkedHashMap<>();
        keys.forEach((key) -> result.put(key, List.of(new Item(key.length(), key, BigDecimal.TEN))));
        return result;
    }

    @Override
    public void touch(long id) {
        this.touched++;
    }

    @Override
    public int count() {
        return 42;
    }

    @Override
    public Item idempotentGet(long id) {
        return get(id);
    }

    @Override
    public String echo(String text) {
        return text;
    }
}
