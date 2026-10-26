package io.github.ilyaslabs.moduleswitch.it;

import io.github.ilyaslabs.moduleswitch.it.shop.ShopApplication;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Starts the fixture applications with inline properties. */
final class Apps {

    private Apps() {
    }

    static ConfigurableApplicationContext shop(String... properties) {
        return run(ShopApplication.class, properties);
    }

    static ConfigurableApplicationContext run(Class<?> application, String... properties) {
        return new SpringApplicationBuilder(application).web(WebApplicationType.NONE)
            .properties("spring.main.banner-mode=off")
            .properties(properties)
            .run();
    }
}
