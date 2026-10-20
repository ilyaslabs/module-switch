package io.github.ilyaslabs.moduleswitch.it;

import io.github.ilyaslabs.moduleswitch.core.CallObservations;
import io.github.ilyaslabs.moduleswitch.it.shop.ShopApplication;
import io.github.ilyaslabs.moduleswitch.it.shop.orders.api.OrdersApi;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Every module call is observed when the application has an ObservationRegistry. */
class ObservabilityTest {

    @Test
    void recordsCallsWithModuleTags() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ShopApplication.class,
                Observations.class)
            .web(WebApplicationType.NONE)
            .properties("module-switch.modules.catalog.mode=local", "module-switch.modules.orders.mode=local")
            .run()) {
            context.getBean(OrdersApi.class).place(1, 1);

            TestObservationRegistry registry = context.getBean(TestObservationRegistry.class);
            TestObservationRegistryAssert.assertThat(registry)
                .hasNumberOfObservationsWithNameEqualTo(CallObservations.NAME, 2)
                .hasAnObservationWithAKeyValue("module", "orders")
                .hasAnObservationWithAKeyValue("module", "catalog")
                .hasAnObservationWithAKeyValue("method", "place")
                .hasAnObservationWithAKeyValue("method", "product")
                .hasAnObservationWithAKeyValue("outcome", "success");
            assertThat(context.getBean(ObservationRegistry.class)).isSameAs(registry);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Observations {

        @Bean
        TestObservationRegistry observationRegistry() {
            return TestObservationRegistry.create();
        }
    }
}
