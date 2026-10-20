plugins {
    id("module-switch.published-library")
}

description = "Module-Switch test helpers, such as ModuleRules.verify()."

dependencies {
    api(project(":module-switch-api"))
    implementation(project(":module-switch-core"))
}
