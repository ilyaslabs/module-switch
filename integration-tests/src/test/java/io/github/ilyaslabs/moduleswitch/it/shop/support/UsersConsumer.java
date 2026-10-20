package io.github.ilyaslabs.moduleswitch.it.shop.support;

import io.github.ilyaslabs.moduleswitch.it.shop.users.api.UsersApi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Code outside any module that needs the users module. Enabled by a test property. */
@Component
@ConditionalOnProperty("test.users-consumer")
class UsersConsumer {

    UsersConsumer(UsersApi users) {
    }
}
