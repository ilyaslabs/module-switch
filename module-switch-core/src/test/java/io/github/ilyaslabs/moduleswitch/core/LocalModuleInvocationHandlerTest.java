package io.github.ilyaslabs.moduleswitch.core;

import java.lang.reflect.Proxy;

import io.github.ilyaslabs.moduleswitch.ModuleException;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.AlphaApi;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.impl.AlphaService;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalModuleInvocationHandlerTest {

    private final AlphaService target = new AlphaService();

    private final TestObservationRegistry observations = TestObservationRegistry.create();

    private final AlphaApi proxy = (AlphaApi) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] { AlphaApi.class },
            new LocalModuleInvocationHandler("alpha", AlphaApi.class, () -> this.target, () -> this.observations));

    @Test
    void delegatesToTheImplementation() {
        assertThat(this.proxy.get(1).name()).isEqualTo("item-1");
        assertThat(this.proxy.count()).isEqualTo(42);
        this.proxy.touch(1);
        assertThat(this.target.touched).isEqualTo(1);
        assertThat(this.proxy.get(0)).isNull();
    }

    @Test
    void moduleExceptionsPassThrough() {
        assertThatThrownBy(() -> this.proxy.get(404)).isInstanceOfSatisfying(ModuleException.NotFound.class,
                (ex) -> assertThat(ex.code()).isEqualTo("ITEM_NOT_FOUND"));
    }

    @Test
    void otherExceptionsBecomeInternal() {
        assertThatThrownBy(() -> this.proxy.get(500)).isInstanceOf(ModuleException.Internal.class)
            .hasMessageContaining("Module 'alpha' failed")
            .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void defaultMethodsRunOnTheProxy() {
        assertThat(this.proxy.greeting()).isEqualTo("hello from default");
    }

    @Test
    void objectMethodsDoNotReachTheImplementation() {
        assertThat(this.proxy.toString()).contains("local").contains(AlphaApi.class.getName());
        assertThat(this.proxy).isEqualTo(this.proxy).isNotEqualTo(this.target);
        assertThat(this.proxy.hashCode()).isEqualTo(System.identityHashCode(this.proxy));
    }

    @Test
    void recordsAnObservationPerCall() {
        this.proxy.get(1);
        assertThatThrownBy(() -> this.proxy.get(404));

        TestObservationRegistryAssert.assertThat(this.observations)
            .hasNumberOfObservationsWithNameEqualTo(CallObservations.NAME, 2)
            .hasAnObservationWithAKeyValue("outcome", "success")
            .hasAnObservationWithAKeyValue("outcome", "NotFound")
            .hasAnObservationWithAKeyValue("mode", "local")
            .hasAnObservationWithAKeyValue("module", "alpha")
            .hasAnObservationWithAKeyValue("method", "get");
    }
}
