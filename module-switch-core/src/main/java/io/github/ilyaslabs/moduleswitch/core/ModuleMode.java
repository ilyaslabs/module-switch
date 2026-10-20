package io.github.ilyaslabs.moduleswitch.core;

/**
 * How a module runs in this deployment.
 */
public enum ModuleMode {

    /** The module's implementation is loaded and called in-process. */
    LOCAL,

    /** The implementation is not loaded; calls go over HTTP to another deployment. */
    HTTP,

    /** Neither loaded nor reachable. The default for every module. */
    OFF
}
