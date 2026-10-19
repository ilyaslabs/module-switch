package io.github.ilyaslabs.moduleswitch.it;

import io.github.ilyaslabs.moduleswitch.core.ModuleSwitchConfigurationException;
import io.github.ilyaslabs.moduleswitch.it.badshop.BadShopApplication;
import io.github.ilyaslabs.moduleswitch.it.noimpl.NoImplApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Misconfigurations stop the application at startup with a message naming the fix. */
@ExtendWith(OutputCaptureExtension.class)
class StartupChecksTest {

    /** The most specific cause of a failed startup. */
    private static Throwable failure(Runnable startup) {
        Throwable thrown = catchThrowable(startup::run);
        assertThat(thrown).as("startup should fail").isNotNull();
        return org.springframework.core.NestedExceptionUtils.getMostSpecificCause(thrown);
    }

    @Test
    void unknownModuleName(CapturedOutput output) {
        assertThat(failure(() -> Apps.shop("module-switch.modules.catalgo.mode=local")))
            .isInstanceOf(ModuleSwitchConfigurationException.class)
            .hasMessageContaining("Unknown module 'catalgo'")
            .hasMessageContaining("Known modules: catalog, orders, reports, users");
        assertThat(output).contains("APPLICATION FAILED TO START").contains("Unknown module 'catalgo'");
    }

    @Test
    void httpModuleWithoutUrl() {
        assertThat(failure(() -> Apps.shop("module-switch.modules.catalog.mode=http")))
            .hasMessageContaining("'catalog' is http but has no url");
    }

    @Test
    void exposingAModuleThatIsNotLocal() {
        assertThat(failure(() -> Apps.shop("module-switch.expose.port=0", "module-switch.expose.modules=users")))
            .hasMessageContaining("Only local modules can be exposed");
    }

    @Test
    void activeModuleNeedingAnOffModule(CapturedOutput output) {
        assertThat(failure(() -> Apps.shop("module-switch.modules.orders.mode=local")))
            .isInstanceOf(NoSuchBeanDefinitionException.class);
        assertThat(output).contains("OrdersService needs CatalogApi, but module 'catalog' is off.")
            .contains("Set module-switch.modules.catalog.mode to local");
    }

    @Test
    void codeOutsideModulesNeedingAnOffModule(CapturedOutput output) {
        assertThat(failure(() -> Apps.shop("test.users-consumer=true")))
            .isInstanceOf(NoSuchBeanDefinitionException.class);
        assertThat(output).contains("UsersConsumer needs UsersApi, but module 'users' is off.");
    }

    @Test
    void customScanBypassingModuleSwitch() {
        assertThat(failure(() -> Apps.shop("test.bypass=true")))
            .isInstanceOf(ModuleSwitchConfigurationException.class)
            .hasMessageContaining("Bean 'usersService'")
            .hasMessageContaining("belongs to module 'users', which is off, but was registered anyway");
    }

    @Test
    void customScanIsFineWhenTheModuleIsLocal() {
        try (ConfigurableApplicationContext context = Apps.shop("test.bypass=true",
                "module-switch.modules.users.mode=local")) {
            assertThat(context.getBeanNamesForType(io.github.ilyaslabs.moduleswitch.it.shop.users.api.UsersApi.class))
                .isNotEmpty();
        }
    }

    @Test
    void contractAndBoundaryViolationsAreAllReported(CapturedOutput output) {
        assertThat(failure(() -> Apps.run(BadShopApplication.class, "module-switch.modules.left.mode=local",
                "module-switch.modules.right.mode=local")))
            .isInstanceOfSatisfying(ModuleSwitchConfigurationException.class, (ex) -> assertThat(ex.getProblems())
                .anySatisfy((p) -> assertThat(p).contains("overloads method 'find'"))
                .anySatisfy((p) -> assertThat(p).contains("LeftApi.unnamed parameter 1 has no @Param"))
                .anySatisfy((p) -> assertThat(p).contains("RightService (module 'right') uses")
                    .contains("LeftService from the implementation of module 'left'")));
        assertThat(output).contains("Module-Switch found 3 problems");
    }

    @Test
    void localModuleWithoutImplementation() {
        assertThat(failure(() -> Apps.run(NoImplApplication.class, "module-switch.modules.ghost.mode=local")))
            .isInstanceOf(ModuleSwitchConfigurationException.class)
            .hasMessageContaining("Module 'ghost' is local but no bean implements")
            .hasMessageContaining("GhostApi");
    }

    @Test
    void warnsWhenNoModuleIsEnabled(CapturedOutput output) {
        try (ConfigurableApplicationContext context = Apps.shop()) {
            assertThat(output).contains("Module-Switch: no modules enabled")
                .contains("Module-Switch modules: catalog=off, orders=off, reports=off, users=off");
        }
    }

    @Test
    void logsEachModuleMode(CapturedOutput output) {
        try (ConfigurableApplicationContext context = Apps.shop("module-switch.modules.users.mode=local",
                "module-switch.modules.catalog.mode=http", "module-switch.modules.catalog.url=http://localhost:1")) {
            assertThat(output).contains("Module-Switch modules: catalog=http, orders=off, reports=off, users=local")
                .doesNotContain("no modules enabled");
        }
    }
}
