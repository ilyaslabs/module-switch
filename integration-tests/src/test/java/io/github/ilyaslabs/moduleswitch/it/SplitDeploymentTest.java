package io.github.ilyaslabs.moduleswitch.it;

import java.math.BigDecimal;
import java.util.List;

import io.github.ilyaslabs.moduleswitch.ModuleException;
import io.github.ilyaslabs.moduleswitch.core.ModuleSwitchServer;
import io.github.ilyaslabs.moduleswitch.it.shop.catalog.api.CatalogApi;
import io.github.ilyaslabs.moduleswitch.it.shop.catalog.api.Product;
import io.github.ilyaslabs.moduleswitch.it.shop.orders.api.Order;
import io.github.ilyaslabs.moduleswitch.it.shop.orders.api.OrdersApi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * catalog split out: one deployment runs catalog and exposes it, the monolith runs orders and
 * calls catalog over HTTP. Same code, different configuration.
 */
class SplitDeploymentTest {

    private static ConfigurableApplicationContext catalogServer;

    private static ConfigurableApplicationContext monolith;

    @BeforeAll
    static void start() {
        catalogServer = Apps.shop("module-switch.modules.catalog.mode=local", "module-switch.expose.port=0",
                "module-switch.expose.modules=catalog", "module-switch.expose.token=internal-secret");
        int port = catalogServer.getBean(ModuleSwitchServer.class).getPort();
        monolith = Apps.shop("module-switch.modules.orders.mode=local", "module-switch.modules.catalog.mode=http",
                "module-switch.modules.catalog.url=http://localhost:" + port,
                "module-switch.modules.catalog.token=internal-secret");
    }

    @AfterAll
    static void stop() {
        monolith.close();
        catalogServer.close();
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void catalogImplementationIsOnlyInTheCatalogDeployment() {
        assertThat(monolith.getBeanNamesForType(CatalogApi.class)).hasSize(1);
        assertThat(monolith.getBean(CatalogApi.class).toString()).contains("remote");
        assertThat(catalogServer.getBeanNamesForType(OrdersApi.class)).isEmpty();
    }

    @Test
    void ordersCallsCatalogOverHttp() {
        Order order = monolith.getBean(OrdersApi.class).place(7, 2);

        assertThat(order).isEqualTo(new Order("product-7", 2, new BigDecimal("39.98")));
    }

    @Test
    void genericResultsSurviveTheWire() {
        List<Product> products = monolith.getBean(CatalogApi.class).search("hats");

        assertThat(products).extracting(Product::category).containsOnly("hats");
        assertThat(products).hasSize(2);
    }

    @Test
    void exceptionsAreTheSameAsInTheMonolith() {
        OrdersApi orders = monolith.getBean(OrdersApi.class);

        assertThatThrownBy(() -> orders.place(404, 1)).isInstanceOfSatisfying(ModuleException.NotFound.class, (ex) -> {
            assertThat(ex).hasMessage("Product 404 not found");
            assertThat(ex.code()).isEqualTo("PRODUCT_NOT_FOUND");
        });
        assertThatThrownBy(() -> orders.place(500, 1)).isInstanceOf(ModuleException.Internal.class)
            .hasMessageContaining("catalog database is down");
    }

    @Test
    void authorizationOfTheIncomingRequestIsForwarded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(monolith.getBean(OrdersApi.class).catalogCaller()).isEqualTo("Bearer user-token");
    }

    @Test
    void withoutAnIncomingRequestNothingIsForwarded() {
        assertThat(monolith.getBean(OrdersApi.class).catalogCaller()).isEqualTo("none");
    }

    @Test
    void wrongTokenIsRejected() {
        int port = catalogServer.getBean(ModuleSwitchServer.class).getPort();
        try (ConfigurableApplicationContext intruder = Apps.shop("module-switch.modules.catalog.mode=http",
                "module-switch.modules.catalog.url=http://localhost:" + port,
                "module-switch.modules.catalog.token=guess")) {
            assertThatThrownBy(() -> intruder.getBean(CatalogApi.class).stock(1))
                .isInstanceOf(ModuleException.Unauthorized.class);
        }
    }

    @Test
    void stoppedModuleIsUnavailable() throws Exception {
        int port;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        try (ConfigurableApplicationContext app = Apps.shop("module-switch.modules.catalog.mode=http",
                "module-switch.modules.catalog.url=http://localhost:" + port,
                "module-switch.modules.catalog.retry.initial-backoff=1ms")) {
            assertThatThrownBy(() -> app.getBean(CatalogApi.class).stock(1))
                .isInstanceOf(ModuleException.Unavailable.class)
                .hasMessageContaining("catalog");
        }
    }
}
