package io.github.ilyaslabs.moduleswitch.it.badshop.right.impl;

import io.github.ilyaslabs.moduleswitch.it.badshop.left.impl.LeftService;

import org.springframework.stereotype.Service;

/** Reaches into left's implementation instead of its API. */
@Service
public class RightService {

    private final LeftService left;

    public RightService(LeftService left) {
        this.left = left;
    }
}
