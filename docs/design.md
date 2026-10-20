# Module-Switch: design (v1)

Status: all decisions agreed with ilyas, 2026-10-19. No code yet; implementation starts only on ilyas's go-ahead.

## 1. Goal

A Spring Boot starter that lets a modular monolith turn any module into a separately deployed service by changing configuration only.

- Every module is `off` unless configured. Only `local` modules are loaded, which cuts startup time and memory.
- Modules talk to each other only through interfaces in an `-api` package. Module-Switch supplies either the local implementation or a generated HTTP client, depending on configuration.
- A module running `local` can be exposed on an internal port, so other deployments can call it over HTTP.

### Non-goals

- gRPC or any transport other than HTTP. HTTP is the only remote transport, now and later.
- Events or messaging between modules. That is the user's business logic. (The docs will warn that in-memory Spring events stop crossing a module once it is split out.)
- Distributed transactions. Each module owns its own data.
- Service discovery or load balancing. A remote module has one `url`; put a k8s Service or load balancer behind it.
- Database migrations. They stay the user's job; the docs will recommend one migrations folder per module.

## 2. Baseline

- JDK 25+, Spring Boot 4, Spring Framework 7.
- Blocking HTTP calls on virtual threads. The client uses the JDK `HttpClient` and the internal server uses the JDK's built-in HTTP server, so the library needs no servlet container and can tell "never sent" failures apart precisely.
- The library is built with Gradle (Kotlin DSL). It's published to Maven Central as plain jars with POM and BOM, so Maven and Gradle apps consume it the same way.

## 3. Library artifacts

**Agreed:** a separate `module-switch-api` jar with no Spring dependency, used by users' `-api` modules; only the app module pulls in the starter. **Agreed:** the full split below.

| Artifact | Contents | Who depends on it |
|---|---|---|
| `module-switch-api` | `@Module`, `@ModuleApi`, `@Idempotent`, `@Param`, `ModuleException` hierarchy. No Spring dependency. | Every user `-api` and impl module |
| `module-switch-core` | Mode resolution, startup checks, local wrapper, HTTP client factory, exposure server, wire protocol | via starter |
| `module-switch-spring-boot-autoconfigure` | Auto-configuration and `@ConfigurationProperties` | via starter |
| `module-switch-spring-boot-starter` | Starter that pulls in the above | The user's application (boot) module |
| `module-switch-test` | `ModuleRules.verify()` and test helpers | User tests |
| `module-switch-bom` | Version alignment | Optional import |

**Agreed:** this repo holds only library code and an extensive test suite. Integration tests use small test-only modules (orders, catalog) inside the test sources, run both as one app and split across two servers. A sample app will live in a separate repository that ilyas provides later.

## 4. How users structure an application

```
rms/
├─ app/                        # @SpringBootApplication, depends on the starter + all modules
├─ order/
│  ├─ order-api/               # interfaces + DTOs only
│  ├─ order-a/                 # implementation
│  └─ order-b/                 # implementation
└─ catalog/
   ├─ catalog-api/
   └─ catalog-impl/
```

Rules:
1. A module's `-api` contains only `@ModuleApi` interfaces and DTOs. Every top-level, non-sealed interface with an abstract method is treated as a module API; DTO hierarchies use sealed interfaces. DTOs must be Jackson-serializable; records are recommended. No entities or Spring beans.
2. Other modules depend only on another module's `-api`, never on its implementation.
3. Membership is declared once per root package in `package-info.java`, and covers all subpackages:

```java
// catalog-api
@ModuleApi("catalog")
package com.ilyasdotdev.rms.catalog.api;

// catalog-impl
@Module("catalog")
package com.ilyasdotdev.rms.catalog.impl;
```

4. Overloaded methods are not allowed in `@ModuleApi` interfaces, because the method name is the endpoint name.
5. Every parameter carries `@Param("name")`.
6. Methods throw only `ModuleException` subtypes to signal errors (see section 8).

### Scanning standards

Module-Switch can only skip what goes through the scanning it controls. So:

- Rely only on `@SpringBootApplication`'s default component scan. Module-Switch plugs a `TypeExcludeFilter` into it.
- **Agreed:** a module declares its data layer with the usual `@EnableJpaRepositories` / `@EntityScan` on a config class inside its own package. That class is skipped when the module isn't `local`, so its repositories and entities are skipped too. These annotations never go on the main class.
- **Agreed:** if a user's own `@ComponentScan` or `@Import` registers beans from a non-`local` module, startup fails naming the bean and what registered it.
- Put module-specific `@Configuration` inside the module's own package, so it is skipped with the module.

The library enforces these at startup (section 6) rather than leaving them to discipline.

## 5. Configuration reference

```yaml
module-switch:
  modules:
    <name>:
      mode: local | http | off      # default off
      url: http://host:8081          # required when mode = http
      token: ${CATALOG_TOKEN}        # optional, sent as X-Module-Switch-Token
      connect-timeout: 2s            # http only, default 2s
      read-timeout: 10s              # http only, default 10s
      retry:
        max-attempts: 3              # http only, default 3 (1 disables)
        initial-backoff: 100ms       # doubles each attempt, with jitter
  expose:
    port: 8081                       # internal port; no port = nothing exposed
    modules: [catalog, orders]       # each must be local
    token: ${INTERNAL_TOKEN}         # optional; if set, every call must carry it
  propagate-headers: [Authorization] # default [Authorization]; trace context always propagated
```

### Example: splitting catalog out

Monolith (`app1`):
```yaml
module-switch:
  modules:
    users:    { mode: local }
    orders:   { mode: local }
    products: { mode: local }
    catalog:  { mode: http, url: http://192.168.0.1:8081 }
  expose:
    port: 8081
    modules: [orders]          # catalog calls back into orders
```

Catalog deployment:
```yaml
module-switch:
  modules:
    catalog: { mode: local }
    orders:  { mode: http, url: http://app1:8081 }
    # users, products: off by default
  expose:
    port: 8081
    modules: [catalog]
```

## 6. Startup behavior

1. Bind properties and resolve each module's mode. **Agreed:** unknown module names in config fail startup, listing the known modules.
2. Find modules: read `@Module` / `@ModuleApi` from `package-info` metadata on the scan path.
3. Filter the component scan: a class in a `@Module(x)` package is skipped unless `x` is `local`. Spring reads metadata only, so skipped classes are never loaded or instantiated.
4. Data access: each module's own config class carries `@EnableJpaRepositories` / `@EntityScan`, so only `local` modules contribute repositories and entities.
5. Register `@ModuleApi` beans for each interface:
   - `local`: a wrapper around the user's implementation bean (see section 7).
   - `http`: a generated HTTP client proxy.
   - `off`: nothing.
6. Validate, and fail startup with a clear message if any of these fail:
   - A bean in an active module injects an api of an `off` module ("catalog needs OrderApi, but module orders is off. Set it to local or http.")
   - A bean outside any module (e.g. a controller in the app's main package) injects an api of an `off` module. Non-module code is treated as an always-on module.
   - A bean from a non-`local` module ended up in the context (it bypassed the scan rules; the message names the bean and how it got there).
   - A bean in one module injects a class from another module's implementation packages.
   - An `@ModuleApi` interface has overloaded methods, or has no implementation in a `local` module.
   - `expose.modules` lists a module that is not `local`.
7. Start the internal server on `expose.port` if configured, after the main context is ready.

`ModuleRules.verify()` runs the same structural checks (rules 1 to 4 of section 4, and the cross-module-impl check) in a unit test, with no running app.

## 7. Call path

### Local
The caller gets a thin wrapper around the user's implementation. It:
- converts any non-`ModuleException` thrown by the implementation into `ModuleException.Internal` (cause kept), so behavior is the same as over HTTP;
- records a Micrometer observation (same metric names as remote calls, tagged `mode=local`).

### HTTP client
Generated as a JDK dynamic proxy backed by the JDK `HttpClient`:
- `POST {url}/{module}/{Interface}/{method}`, for example `POST http://192.168.0.1:8081/catalog/CatalogApi/getProduct`.
- **Agreed:** every call is a `POST`, and the body is a JSON object keyed by parameter name, e.g. `{"category": "shoes", "page": 2}`, so reordering parameters doesn't break callers running an older version.
- **Agreed:** every parameter of an `@ModuleApi` method must carry `@Param("name")`; that name is the JSON key, independent of compiler flags and Java variable names. A missing `@Param` fails startup (and `ModuleRules.verify()`).
- **Agreed:** the server answers `200` with a JSON body, or `204` for `void` methods and `null` results (the client maps `204` back to `null`). For `void` methods the client accepts any `2xx`. Generic return types are deserialized from the method's full generic signature.
- Headers: `X-Module-Switch-Token` if configured, the propagated headers from the current request, and trace context.
- Errors are mapped back to `ModuleException` subtypes (section 8).

### Exposure server
A separate server on `expose.port` (the JDK's built-in HTTP server on virtual threads), **agreed:** fully separate from the application's web server, so it shares nothing with the public port's filters and security. Implementations read forwarded headers through `ModuleCallContext`. For each exposed module and each method, it:
1. checks the token if `expose.token` is set (missing or wrong gives `401`);
2. returns `503` with `X-Module-Switch-Not-Ready` while the app is starting or shutting down, before invoking anything;
3. deserializes arguments, calls the local wrapper and serializes the result or error.

## 8. Errors

The library owns the vocabulary. All are unchecked, and each carries `message`, optional `code` (such as `"PRODUCT_NOT_FOUND"`) and a `details` map.

| Type | HTTP |
|---|---|
| `ModuleException.BadRequest` | 400 |
| `ModuleException.Unauthorized` | 401 |
| `ModuleException.Forbidden` | 403 |
| `ModuleException.NotFound` | 404 |
| `ModuleException.Conflict` | 409 |
| `ModuleException.Internal` | 500 |
| `ModuleException.Unavailable` | 503, and also used when the remote module can't be reached |

**Agreed:** on the wire the error is an RFC 9457 `application/problem+json` body with `code` and `details` extensions. The client rebuilds the same subtype.

## 9. Timeouts and retries

- Per-module `connect-timeout` (2s) and `read-timeout` (10s), for `http` mode only.
- Retries apply in `http` mode only, and by default only when the request certainly never ran:
  - connection refused, DNS failure, connect timeout;
  - a `503` with `X-Module-Switch-Not-Ready`.
- Methods annotated `@Idempotent` in the api are also retried on a read timeout and on `502`, `503` and `504`.
- Never retried: any other `ModuleException`, and read timeouts on non-idempotent methods.
- Up to `max-attempts` (3), exponential backoff from `initial-backoff` (100ms) with jitter. After the last attempt the caller gets `ModuleException.Unavailable`.

## 10. Observability (agreed)

- Micrometer observation per call: `module.switch.call`, tagged with module, interface, method, mode and outcome.
- Trace context propagated on every remote call.
- At startup, one INFO log line per module showing its mode, plus the exposed port and modules.
- If every module is `off`, startup logs a WARN: "Module-Switch: no modules enabled".

## 11. Still open

1. ~~Maven coordinates~~ **Agreed:** group `io.github.ilyaslabs` (already registered on Maven Central), base package `io.github.ilyaslabs.moduleswitch`.
2. ~~License~~ **Agreed:** Apache-2.0.
3. ~~Which Spring Data stores~~ Resolved by the data-layer decision: because each module puts its own `@Enable...Repositories` on its own config class, the library is store-agnostic (JPA, JDBC, Mongo all work the same way).
