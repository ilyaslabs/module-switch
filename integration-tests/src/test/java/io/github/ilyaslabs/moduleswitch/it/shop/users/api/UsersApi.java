package io.github.ilyaslabs.moduleswitch.it.shop.users.api;

import io.github.ilyaslabs.moduleswitch.Param;

public interface UsersApi {

    String name(@Param("id") long id);
}
