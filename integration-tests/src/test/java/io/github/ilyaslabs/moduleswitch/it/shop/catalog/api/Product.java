package io.github.ilyaslabs.moduleswitch.it.shop.catalog.api;

import java.math.BigDecimal;

public record Product(long id, String name, String category, BigDecimal price) {
}
