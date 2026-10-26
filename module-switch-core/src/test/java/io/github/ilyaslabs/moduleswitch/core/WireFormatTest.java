package io.github.ilyaslabs.moduleswitch.core;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.github.ilyaslabs.moduleswitch.ModuleException;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.AlphaApi;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.Item;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WireFormatTest {

    private final WireFormat wire = new WireFormat(JsonMapper.builder().build());

    private static Method method(String name) {
        return ApiContract.operationsByName(AlphaApi.class).get(name);
    }

    private static byte[] json(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void pathIsModuleInterfaceAndMethod() {
        assertThat(WireFormat.path("alpha", AlphaApi.class, method("get"))).isEqualTo("/alpha/AlphaApi/get");
    }

    @Test
    void argumentsAreKeyedByParamName() {
        byte[] body = this.wire.writeArguments(method("search"), new Object[] { "shoes", 2 });

        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("{\"query\":\"shoes\",\"limit\":2}");
    }

    @Test
    void argumentsRoundTripRegardlessOfOrder() {
        Object[] args = this.wire.readArguments(method("search"), json("{\"limit\":3,\"query\":\"hats\"}"));

        assertThat(args).containsExactly("hats", 3);
    }

    @Test
    void missingArgumentsAreNullOrZeroAndUnknownFieldsAreIgnored() {
        Object[] args = this.wire.readArguments(method("search"), json("{\"extra\":true}"));

        assertThat(args).containsExactly(null, 0);
    }

    @Test
    void emptyBodyMeansNoArguments() {
        assertThat(this.wire.readArguments(method("count"), new byte[0])).isEmpty();
    }

    @Test
    void genericArgumentsKeepTheirTypes() {
        Object[] args = this.wire.readArguments(method("grouped"), json("{\"keys\":[\"a\",\"b\"]}"));

        assertThat(args[0]).isEqualTo(List.of("a", "b"));
    }

    @Test
    void invalidArgumentIsBadRequest() {
        assertThatThrownBy(() -> this.wire.readArguments(method("get"), json("{\"id\":\"not-a-number\"}")))
            .isInstanceOfSatisfying(ModuleException.BadRequest.class, (ex) -> {
                assertThat(ex.code()).isEqualTo("INVALID_PARAMETER");
                assertThat(ex.details()).containsEntry("parameter", "id");
            });
    }

    @Test
    void bodyMustBeAnObject() {
        assertThatThrownBy(() -> this.wire.readArguments(method("get"), json("[1]")))
            .isInstanceOf(ModuleException.BadRequest.class);
    }

    @Test
    void resultsRoundTripWithGenerics() {
        Map<String, List<Item>> value = Map.of("k", List.of(new Item(1, "a", new BigDecimal("1.50"))));

        Object read = this.wire.readResult(method("grouped"), this.wire.writeResult(value));

        assertThat(read).isEqualTo(value);
    }

    @Test
    void emptyResultIsNullButPrimitiveResultMustNotBeEmpty() {
        assertThat(this.wire.readResult(method("get"), new byte[0])).isNull();
        assertThatThrownBy(() -> this.wire.readResult(method("count"), new byte[0]))
            .isInstanceOf(ModuleException.Internal.class);
    }

    @Test
    void problemsRoundTrip() {
        ModuleException original = new ModuleException.NotFound("Item 7 not found", "ITEM_NOT_FOUND", Map.of("id", 7));

        byte[] body = this.wire.writeProblem(original);
        ModuleException read = this.wire.readProblem(404, body, "fallback");

        assertThat(new String(body, StandardCharsets.UTF_8)).contains("\"status\":404", "\"type\":\"about:blank\"");
        assertThat(read).isInstanceOf(ModuleException.NotFound.class).hasMessage("Item 7 not found");
        assertThat(read.code()).isEqualTo("ITEM_NOT_FOUND");
        assertThat(read.details()).containsEntry("id", 7);
    }

    @Test
    void problemWithoutCodeOrDetailsOmitsThem() {
        String body = new String(this.wire.writeProblem(new ModuleException.Conflict("taken")), StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("\"code\"", "\"details\"");
    }

    @Test
    void nonJsonErrorBodyFallsBackToStatus() {
        ModuleException read = this.wire.readProblem(502, json("<html>Bad gateway</html>"), "Module answered HTTP 502");

        assertThat(read).isInstanceOf(ModuleException.Unavailable.class).hasMessage("Module answered HTTP 502");
        assertThat(read.code()).isNull();
    }

    @Test
    void emptyErrorBodyFallsBackToStatus() {
        assertThat(this.wire.readProblem(403, new byte[0], "denied")).isInstanceOf(ModuleException.Forbidden.class);
    }
}
