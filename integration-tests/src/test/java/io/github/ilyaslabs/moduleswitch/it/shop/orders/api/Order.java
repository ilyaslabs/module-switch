package io.github.ilyaslabs.moduleswitch.it.shop.orders.api;

import java.math.BigDecimal;

public record Order(String productName, int quantity, BigDecimal total) {
}
