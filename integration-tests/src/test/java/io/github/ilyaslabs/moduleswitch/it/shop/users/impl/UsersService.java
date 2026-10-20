package io.github.ilyaslabs.moduleswitch.it.shop.users.impl;

import io.github.ilyaslabs.moduleswitch.it.shop.users.api.UsersApi;

import org.springframework.stereotype.Service;

@Service
public class UsersService implements UsersApi {

    @Override
    public String name(long id) {
        return "user-" + id;
    }
}
