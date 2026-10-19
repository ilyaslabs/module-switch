# Module-Switch

Run a Spring Boot modular monolith today, and move any module into its own service later by changing configuration only.

- Every module is **off** unless configured. Only `local` modules are loaded, so unused modules cost no startup time.
- Modules talk to each other only through interfaces in an `-api` package. Callers get the local implementation or a generated HTTP client, depending on configuration.
- A `local` module can be exposed on an internal port so other deployments can call it.

Requires JDK 25+ and Spring Boot 4. The full design is in [docs/design.md](docs/design.md).

## Install

Maven:

```xml
<!-- application module -->
<dependency>
  <groupId>io.github.ilyaslabs</groupId>
  <artifactId>module-switch-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>

<!-- each <module>-api, no Spring needed -->
<dependency>
  <groupId>io.github.ilyaslabs</groupId>
  <artifactId>module-switch-api</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("io.github.ilyaslabs:module-switch-spring-boot-starter:0.1.0")   // application
implementation("io.github.ilyaslabs:module-switch-api:0.1.0")                    // each -api
testImplementation("io.github.ilyaslabs:module-switch-test:0.1.0")
```

## Structure your modules

```
rms/
├─ app/                 @SpringBootApplication
├─ catalog/
│  ├─ catalog-api/      interfaces + DTOs only
│  └─ catalog-impl/     implementation
└─ order/
   ├─ order-api/
   └─ order-impl/
```

Declare membership once per root package in `package-info.java`; it covers every subpackage:

```java
@ModuleApi("catalog")
package com.example.rms.catalog.api;

@Module("catalog")
package com.example.rms.catalog.impl;
```

Write the API as a plain interface. Every parameter needs `@Param`, and methods can't be overloaded:

```java
public interface CatalogApi {

    Product product(@Param("id") long id);

    @Idempotent
    List<Product> search(@Param("category") String category, @Param("page") int page);
}
```

Implement it as a normal bean in the implementation package, and inject `CatalogApi` anywhere else.

Signal errors only with `ModuleException` subtypes (`NotFound`, `BadRequest`, `Unauthorized`, `Forbidden`, `Conflict`, `Unavailable`, `Internal`). Callers see the same exception whether the module is local or remote; anything else arrives as `ModuleException.Internal`.

## Configure

```yaml
module-switch:
  modules:
    catalog:
      mode: local | http | off      # default off
      url: http://catalog:8081       # required for http
      token: ${CATALOG_TOKEN}        # optional, sent as X-Module-Switch-Token
      connect-timeout: 2s
      read-timeout: 10s
      retry:
        max-attempts: 3
        initial-backoff: 100ms
  expose:
    port: 8081                       # internal port; unset = nothing exposed
    modules: [catalog]               # must be local; served under /catalog
    token: ${INTERNAL_TOKEN}         # optional; required on every call when set
  propagate-headers: [Authorization]
```

Splitting catalog out means the monolith sets `catalog: { mode: http, url: http://catalog:8081 }`, and the new catalog deployment sets `catalog: { mode: local }` with `expose.modules: [catalog]`.

## Rules the library enforces at startup

- Unknown module names, `http` without `url`, or exposing a module that isn't `local`.
- Any code that injects the API of an `off` module.
- An API that overloads a method or misses `@Param`.
- A bean of a non-local module registered anyway, for example through your own `@ComponentScan`, `@Import` or `@Enable...Repositories` outside the module's packages. Put data-layer annotations on a config class inside the module.
- Code that uses another module's implementation classes instead of its API.

Check the same structure in a unit test, without starting the app:

```java
@Test
void modulesFollowTheRules() {
    ModuleRules.verify("com.example.rms");
}
```

## Remote calls

- Every call is `POST {url}/{module}/{Interface}/{method}` with a JSON object keyed by `@Param` names.
- Retries happen only when the request certainly never ran (connection refused, unknown host, connect timeout, server not ready). `@Idempotent` methods are also retried after a read timeout and on 502, 503 and 504.
- `Authorization` (configurable) and trace context are forwarded. On the serving side, read them with `ModuleCallContext.current()`.
- Every call records a Micrometer observation named `module.switch.call`, tagged with module, interface, method, mode and outcome.

## Build

```
./gradlew build
```

## License

Apache License 2.0
