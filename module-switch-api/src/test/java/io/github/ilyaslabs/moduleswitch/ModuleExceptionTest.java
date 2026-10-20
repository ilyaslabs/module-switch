package io.github.ilyaslabs.moduleswitch;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleExceptionTest {

    @Test
    void carriesMessageCodeAndDetails() {
        ModuleException ex = new ModuleException.NotFound("Product 42 not found", "PRODUCT_NOT_FOUND", Map.of("id", 42));

        assertThat(ex).hasMessage("Product 42 not found");
        assertThat(ex.status()).isEqualTo(404);
        assertThat(ex.code()).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(ex.details()).containsEntry("id", 42);
    }

    @Test
    void detailsAreNeverNullAndImmutable() {
        ModuleException ex = new ModuleException.Conflict("taken");

        assertThat(ex.code()).isNull();
        assertThat(ex.details()).isEmpty();
        assertThatThrownBy(() -> ex.details().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void detailsAreCopied() {
        Map<String, Object> source = new HashMap<>();
        source.put("a", 1);
        ModuleException ex = new ModuleException.BadRequest("bad", null, source);
        source.put("b", 2);

        assertThat(ex.details()).containsOnlyKeys("a");
    }

    @Test
    void keepsCause() {
        IllegalStateException cause = new IllegalStateException("boom");
        ModuleException ex = new ModuleException.Internal("failed", null, null, cause);

        assertThat(ex).hasCause(cause);
    }

    @ParameterizedTest
    @CsvSource({
            "400, BadRequest", "401, Unauthorized", "403, Forbidden", "404, NotFound", "409, Conflict",
            "500, Internal", "502, Unavailable", "503, Unavailable", "504, Unavailable",
            "418, BadRequest", "422, BadRequest", "501, Internal", "599, Internal", "302, Internal" })
    void forStatusPicksSubtype(int status, String type) {
        ModuleException ex = ModuleException.forStatus(status, "m", "C", Map.of("k", "v"), null);

        assertThat(ex.getClass().getSimpleName()).isEqualTo(type);
        assertThat(ex).hasMessage("m");
        assertThat(ex.code()).isEqualTo("C");
        assertThat(ex.details()).containsEntry("k", "v");
    }

    @ParameterizedTest
    @CsvSource({ "400", "401", "403", "404", "409", "500", "503" })
    void subtypeStatusRoundTripsThroughForStatus(int status) {
        ModuleException ex = ModuleException.forStatus(status, "m", null, null, null);

        assertThat(ex.status()).isEqualTo(status);
    }
}
