package io.github.ilyaslabs.moduleswitch.it.badshop.left.impl;

import io.github.ilyaslabs.moduleswitch.it.badshop.left.api.LeftApi;

import org.springframework.stereotype.Service;

@Service
public class LeftService implements LeftApi {

    @Override
    public String find(long id) {
        return "id";
    }

    @Override
    public String find(String sku) {
        return "sku";
    }

    @Override
    public String unnamed(String value) {
        return value;
    }
}
