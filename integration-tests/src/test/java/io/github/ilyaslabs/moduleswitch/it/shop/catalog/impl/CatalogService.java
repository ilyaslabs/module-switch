package io.github.ilyaslabs.moduleswitch.it.shop.catalog.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import io.github.ilyaslabs.moduleswitch.ModuleException;
import io.github.ilyaslabs.moduleswitch.core.ModuleCallContext;
import io.github.ilyaslabs.moduleswitch.it.shop.catalog.api.CatalogApi;
import io.github.ilyaslabs.moduleswitch.it.shop.catalog.api.Product;

import org.springframework.stereotype.Service;

@Service
class CatalogService implements CatalogApi {

    @Override
    public Product product(long id) {
        if (id == 404) {
            throw new ModuleException.NotFound("Product 404 not found", "PRODUCT_NOT_FOUND", Map.of("id", id));
        }
        if (id == 500) {
            throw new IllegalStateException("catalog database is down");
        }
        return new Product(id, "product-" + id, "shoes", new BigDecimal("19.99"));
    }

    @Override
    public List<Product> search(String category) {
        return List.of(new Product(1, "a", category, BigDecimal.ONE), new Product(2, "b", category, BigDecimal.TEN));
    }

    @Override
    public String caller() {
        return ModuleCallContext.current().flatMap((context) -> context.header("Authorization")).orElse("none");
    }

    @Override
    public int stock(long id) {
        return (int) id * 10;
    }
}
