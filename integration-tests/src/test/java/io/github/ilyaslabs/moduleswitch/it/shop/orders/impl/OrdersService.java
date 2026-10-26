package io.github.ilyaslabs.moduleswitch.it.shop.orders.impl;

import java.math.BigDecimal;

import io.github.ilyaslabs.moduleswitch.it.shop.catalog.api.CatalogApi;
import io.github.ilyaslabs.moduleswitch.it.shop.catalog.api.Product;
import io.github.ilyaslabs.moduleswitch.it.shop.orders.api.Order;
import io.github.ilyaslabs.moduleswitch.it.shop.orders.api.OrdersApi;

import org.springframework.stereotype.Service;

@Service
class OrdersService implements OrdersApi {

    private final CatalogApi catalog;

    OrdersService(CatalogApi catalog) {
        this.catalog = catalog;
    }

    @Override
    public Order place(long productId, int quantity) {
        Product product = this.catalog.product(productId);
        return new Order(product.name(), quantity, product.price().multiply(BigDecimal.valueOf(quantity)));
    }

    @Override
    public String catalogCaller() {
        return this.catalog.caller();
    }
}
