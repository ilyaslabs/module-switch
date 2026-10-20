package io.github.ilyaslabs.moduleswitch.it;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;

import io.github.ilyaslabs.moduleswitch.ModuleException;
import io.github.ilyaslabs.moduleswitch.core.ModuleSwitchServer;
import io.github.ilyaslabs.moduleswitch.it.shop.catalog.api.CatalogApi;
import io.github.ilyaslabs.moduleswitch.it.shop.orders.api.Order;
import io.github.ilyaslabs.moduleswitch.it.shop.orders.api.OrdersApi;
import io.github.ilyaslabs.moduleswitch.it.shop.users.api.UsersApi;
import io.github.ilyaslabs.moduleswitch.it.shop.users.impl.UsersService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** catalog and orders local, users off: everything in one process. */
class MonolithTest {

    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void start() {
        context = Apps.shop("module-switch.modules.catalog.mode=local", "module-switch.modules.orders.mode=local");
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @Test
    void modulesCallEachOtherInProcess() {
        Order order = context.getBean(OrdersApi.class).place(7, 3);

        assertThat(order).isEqualTo(new Order("product-7", 3, new BigDecimal("59.97")));
    }

    @Test
    void callersGetTheModuleSwitchWrapper() {
        CatalogApi catalog = context.getBean(CatalogApi.class);

        assertThat(Proxy.isProxyClass(catalog.getClass())).isTrue();
        assertThat(catalog.toString()).contains("local");
        assertThat(context.getBeansOfType(CatalogApi.class)).hasSize(2);
    }

    @Test
    void exceptionsBehaveAsTheyWouldRemotely() {
        OrdersApi orders = context.getBean(OrdersApi.class);

        assertThatThrownBy(() -> orders.place(404, 1)).isInstanceOf(ModuleException.NotFound.class)
            .hasMessage("Product 404 not found");
        assertThatThrownBy(() -> orders.place(500, 1)).isInstanceOf(ModuleException.Internal.class)
            .hasMessageContaining("catalog database is down");
    }

    @Test
    void offModulesAreNotLoaded() {
        assertThat(context.getBeanNamesForType(UsersApi.class)).isEmpty();
        assertThat(context.getBeanNamesForType(UsersService.class)).isEmpty();
    }

    @Test
    void classesOfOffModulesAreNeverInitialized() {
        assertThat(context.getBeanNamesForType(io.github.ilyaslabs.moduleswitch.it.shop.reports.api.ReportsApi.class))
            .isEmpty();
        assertThat(System.getProperty("module-switch.it.reports-loaded")).isNull();
    }

    @Test
    void nothingIsExposedWithoutAPort() {
        assertThat(context.getBeanNamesForType(ModuleSwitchServer.class)).isEmpty();
    }

    @Test
    void noHeadersAreForwardedInProcess() {
        assertThat(context.getBean(OrdersApi.class).catalogCaller()).isEqualTo("none");
    }
}
