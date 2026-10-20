package io.github.ilyaslabs.moduleswitch.it.shop.orders.api;

import io.github.ilyaslabs.moduleswitch.Param;

public interface OrdersApi {

    Order place(@Param("productId") long productId, @Param("quantity") int quantity);

    /** What catalog sees as the caller when orders calls it. */
    String catalogCaller();
}
