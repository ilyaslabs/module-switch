package io.github.ilyaslabs.moduleswitch.core;

/**
 * The module a package belongs to, and whether it is the module's API or implementation.
 *
 * @param module the module name
 * @param kind API or IMPL
 * @param rootPackage the package whose {@code package-info} declares the membership
 */
public record ModuleMembership(String module, Kind kind, String rootPackage) {

    public enum Kind {
        /** Declared with {@code @ModuleApi}: interfaces and DTOs, always available. */
        API,
        /** Declared with {@code @Module}: loaded only in local mode. */
        IMPL
    }

    public boolean isApi() {
        return this.kind == Kind.API;
    }

    public boolean isImpl() {
        return this.kind == Kind.IMPL;
    }
}
