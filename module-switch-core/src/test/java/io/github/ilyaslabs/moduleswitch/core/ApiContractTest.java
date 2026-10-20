package io.github.ilyaslabs.moduleswitch.core;

import java.lang.reflect.Method;

import io.github.ilyaslabs.moduleswitch.core.badapi.BadApi;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.AlphaApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiContractTest {

    @Test
    void validApiHasNoProblems() {
        assertThat(ApiContract.validate(AlphaApi.class)).isEmpty();
    }

    @Test
    void reportsEveryRuleViolation() {
        assertThat(ApiContract.validate(BadApi.class)).hasSize(4)
            .anySatisfy((p) -> assertThat(p).contains("overloads method 'find'"))
            .anySatisfy((p) -> assertThat(p).contains("BadApi.missing parameter 1 has no @Param"))
            .anySatisfy((p) -> assertThat(p).contains("BadApi.blank parameter 1 has a blank @Param name"))
            .anySatisfy((p) -> assertThat(p).contains("BadApi.twice parameter 2 repeats @Param name 'a'"));
    }

    @Test
    void operationsExcludeDefaultMethods() {
        assertThat(ApiContract.operations(AlphaApi.class)).extracting(Method::getName)
            .contains("get", "search", "touch")
            .doesNotContain("greeting");
        assertThat(ApiContract.operationsByName(AlphaApi.class)).containsKey("count");
    }

    @Test
    void parameterNamesComeFromParam() throws Exception {
        Method search = AlphaApi.class.getMethod("search", String.class, int.class);

        assertThat(ApiContract.parameterNames(search)).containsExactly("query", "limit");
    }
}
