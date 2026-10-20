# Module-Switch developer guide

This guide takes you from an empty Spring Boot project to a modular monolith that can move any module into its own service by changing configuration only. Follow the steps in order; each one builds on the previous one.

Throughout the guide we build a small shop with two modules:

- **catalog** knows about products.
- **orders** places orders, and asks catalog for product details.

---

## Contents

1. [How Module-Switch works in one minute](#1-how-module-switch-works-in-one-minute)
2. [Requirements](#2-requirements)
3. [Lay out your project](#3-lay-out-your-project)
4. [Add the dependencies](#4-add-the-dependencies)
5. [Write a module's API](#5-write-a-modules-api)
6. [Write the module's implementation](#6-write-the-modules-implementation)
7. [Call one module from another](#7-call-one-module-from-another)
8. [Turn modules on](#8-turn-modules-on)
9. [Report errors](#9-report-errors)
10. [Check your structure in a unit test](#10-check-your-structure-in-a-unit-test)
11. [Split a module into its own service](#11-split-a-module-into-its-own-service)
12. [Secure and tune remote calls](#12-secure-and-tune-remote-calls)
13. [Monitor module calls](#13-monitor-module-calls)
14. [Move a module to its own repository](#14-move-a-module-to-its-own-repository)
15. [Startup errors and how to fix them](#15-startup-errors-and-how-to-fix-them)
16. [Rules checklist](#16-rules-checklist)
17. [Configuration reference](#17-configuration-reference)

---

## 1. How Module-Switch works in one minute

Every module has two parts:

- an **API**: Java interfaces and data classes that other modules use;
- an **implementation**: the code behind those interfaces.

Each module gets one setting, `mode`:

| Mode | What happens in this application |
|---|---|
| `off` (default) | The module is not loaded and not reachable. |
| `local` | The implementation is loaded and called in the same process. |
| `http` | The implementation is **not** loaded. Calls go over HTTP to another application where the module is `local`. |

Other modules always inject the **interface**. Module-Switch decides what they get: the local implementation, or a generated HTTP client. Your code never changes; only `application.yaml` does.

---

## 2. Requirements

- JDK 25 or newer
- Spring Boot 4
- Maven or Gradle (both work the same way)

---

## 3. Lay out your project

Give every module an `-api` part and an implementation part. A multi-module build is recommended, so the compiler stops one module from touching another's implementation:

```
shop/
├─ app/                    # the Spring Boot application (main class)
├─ catalog/
│  ├─ catalog-api/         # interfaces + data classes only
│  └─ catalog-impl/        # implementation
└─ orders/
   ├─ orders-api/
   └─ orders-impl/
```

Dependencies between them:

| Module | Depends on |
|---|---|
| `catalog-api` | `module-switch-api` |
| `catalog-impl` | `catalog-api`, Spring |
| `orders-api` | `module-switch-api` |
| `orders-impl` | `orders-api`, **`catalog-api`** (never `catalog-impl`) |
| `app` | `module-switch-spring-boot-starter`, every `-api` and every `-impl` |

Keep all packages under one root package, the package of your main class. In this guide that is `com.example.shop`:

```
com.example.shop                     ShopApplication (main class)
com.example.shop.catalog.api         catalog-api
com.example.shop.catalog.impl        catalog-impl
com.example.shop.orders.api          orders-api
com.example.shop.orders.impl         orders-impl
```

> A single-module project also works. Module-Switch looks at **packages**, not build modules. Separate build modules just give you an extra compile-time safety net.

---

## 4. Add the dependencies

Replace `0.1.0` with the latest version.

### Maven

In `app/pom.xml`:

```xml
<dependency>
  <groupId>io.github.ilyaslabs</groupId>
  <artifactId>module-switch-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>io.github.ilyaslabs</groupId>
  <artifactId>module-switch-test</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```

In every `-api` module (this jar has **no** Spring dependency):

```xml
<dependency>
  <groupId>io.github.ilyaslabs</groupId>
  <artifactId>module-switch-api</artifactId>
  <version>0.1.0</version>
</dependency>
```

Optionally import the BOM once in your parent POM so all versions stay aligned:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.ilyaslabs</groupId>
      <artifactId>module-switch-bom</artifactId>
      <version>0.1.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

### Gradle (Kotlin DSL)

```kotlin
// app/build.gradle.kts
implementation("io.github.ilyaslabs:module-switch-spring-boot-starter:0.1.0")
testImplementation("io.github.ilyaslabs:module-switch-test:0.1.0")

// catalog-api/build.gradle.kts (and every other -api)
implementation("io.github.ilyaslabs:module-switch-api:0.1.0")
```

---

## 5. Write a module's API

### 5.1 Mark the package

Create `package-info.java` in the root package of `catalog-api`. This one file covers the package and every package beneath it.

```java
// catalog-api/src/main/java/com/example/shop/catalog/api/package-info.java
@ModuleApi("catalog")
package com.example.shop.catalog.api;

import io.github.ilyaslabs.moduleswitch.ModuleApi;
```

The name (`catalog`) is the name you will use in `application.yaml`.

### 5.2 Write the data classes

Use records. They must be plain data that converts to JSON (no entities, no Spring beans):

```java
package com.example.shop.catalog.api;

import java.math.BigDecimal;

public record Product(long id, String name, BigDecimal price) {
}
```

### 5.3 Write the interface

```java
package com.example.shop.catalog.api;

import java.util.List;

import io.github.ilyaslabs.moduleswitch.Idempotent;
import io.github.ilyaslabs.moduleswitch.Param;

public interface CatalogApi {

    Product getProduct(@Param("id") long id);

    @Idempotent
    List<Product> search(@Param("category") String category, @Param("page") int page);

    void markViewed(@Param("id") long id);
}
```

Three rules for API interfaces:

1. **Put `@Param("name")` on every parameter.** The name is the JSON field name on the wire, so you can rename or reorder Java parameters later without breaking callers.
2. **Don't overload methods.** The method name is part of the URL. Use `getProductById` and `getProductBySku` instead of two `getProduct` methods.
3. **Mark read-only methods `@Idempotent`** if they are safe to run twice. They get extra retries (see [step 12](#12-secure-and-tune-remote-calls)).

> Every top-level, non-sealed interface with at least one abstract method in an API package counts as a module API. If you need an interface for data (for example a `Shape` with `Circle` and `Square`), make it `sealed`, and it is treated as data.

Default methods are allowed; they run on the caller's side.

---

## 6. Write the module's implementation

### 6.1 Mark the package

```java
// catalog-impl/src/main/java/com/example/shop/catalog/impl/package-info.java
@Module("catalog")
package com.example.shop.catalog.impl;

import io.github.ilyaslabs.moduleswitch.Module;
```

Everything in this package and below belongs to catalog, and is only loaded when catalog is `local`.

### 6.2 Implement the interface

A normal Spring bean, nothing special:

```java
package com.example.shop.catalog.impl;

import java.util.List;

import com.example.shop.catalog.api.CatalogApi;
import com.example.shop.catalog.api.Product;

import org.springframework.stereotype.Service;

@Service
class CatalogService implements CatalogApi {

    private final ProductRepository products;

    CatalogService(ProductRepository products) {
        this.products = products;
    }

    @Override
    public Product getProduct(long id) {
        // ...
    }

    @Override
    public List<Product> search(String category, int page) {
        // ...
    }

    @Override
    public void markViewed(long id) {
        // ...
    }
}
```

Exactly one bean must implement each API interface of a `local` module.

### 6.3 Repositories and entities stay inside the module

If the module uses Spring Data, put the `@Enable...Repositories` and `@EntityScan` annotations on a configuration class **inside the module's package**:

```java
package com.example.shop.catalog.impl;

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories          // scans com.example.shop.catalog.impl and below
@EntityScan                      // same package
class CatalogPersistenceConfig {
}
```

When catalog is not `local`, this class is skipped, so its repositories and entities are skipped too. The same works for `@EnableJdbcRepositories`, `@EnableMongoRepositories` and the others.

**Never put these annotations on your main class.** Module-Switch would then see catalog's repositories in an application where catalog is off, and stop at startup (see [step 15](#15-startup-errors-and-how-to-fix-them)).

Database migrations are yours to manage. One migrations folder per module (for example `db/migration/catalog`) keeps them easy to move with the module later.

---

## 7. Call one module from another

Inject the **interface**. Never inject another module's implementation class.

```java
package com.example.shop.orders.impl;

import com.example.shop.catalog.api.CatalogApi;
import com.example.shop.catalog.api.Product;
import com.example.shop.orders.api.Order;
import com.example.shop.orders.api.OrdersApi;

import org.springframework.stereotype.Service;

@Service
class OrdersService implements OrdersApi {

    private final CatalogApi catalog;

    OrdersService(CatalogApi catalog) {
        this.catalog = catalog;
    }

    @Override
    public Order place(long productId, int quantity) {
        Product product = this.catalog.getProduct(productId);
        // ...
    }
}
```

What `CatalogApi` turns out to be is decided by configuration:

- catalog `local`: a thin wrapper around `CatalogService` in the same process.
- catalog `http`: a generated HTTP client that calls the catalog service.

---

## 8. Turn modules on

**Every module is off by default.** With nothing in `application.yaml`, the application starts but no module is loaded, and you see this warning:

```
Module-Switch: no modules enabled. Every module is off; set module-switch.modules.<name>.mode to local or http.
```

Turn on the modules this application should run:

```yaml
# app/src/main/resources/application.yaml
module-switch:
  modules:
    catalog:
      mode: local
    orders:
      mode: local
```

Start the application. One log line confirms each module's mode:

```
Module-Switch modules: catalog=local, orders=local
```

You now have a modular monolith. Modules you don't list stay off, and their classes are never loaded, which keeps startup fast.

> If a `local` or always-on part of your app injects the API of an `off` module, the application stops at startup and tells you which module to turn on.

---

## 9. Report errors

Inside an implementation, signal expected errors only with `ModuleException` subtypes. Each one maps to an HTTP status, so callers see the **same exception** whether the module is local or remote.

| Exception | HTTP status | Use it when |
|---|---|---|
| `ModuleException.BadRequest` | 400 | The input is invalid |
| `ModuleException.Unauthorized` | 401 | The caller isn't authenticated |
| `ModuleException.Forbidden` | 403 | The caller isn't allowed to do this |
| `ModuleException.NotFound` | 404 | Something doesn't exist |
| `ModuleException.Conflict` | 409 | The request clashes with current state |
| `ModuleException.Unavailable` | 503 | The module can't be reached or isn't ready (the library throws this itself) |
| `ModuleException.Internal` | 500 | Something failed unexpectedly |

Each one carries a message, an optional machine-readable `code` and an optional `details` map:

```java
throw new ModuleException.NotFound(
        "Product " + id + " not found",
        "PRODUCT_NOT_FOUND",
        Map.of("id", id));
```

The caller handles it the same way in every mode:

```java
try {
    catalog.getProduct(id);
}
catch (ModuleException.NotFound ex) {
    if ("PRODUCT_NOT_FOUND".equals(ex.code())) {
        // ...
    }
}
```

Any other exception thrown by an implementation (a `NullPointerException`, a database error) reaches callers as `ModuleException.Internal`, in local and http mode alike. That way nothing changes in behavior when you split a module out.

---

## 10. Check your structure in a unit test

Add one test to the `app` module. It checks every module rule without starting Spring:

```java
import io.github.ilyaslabs.moduleswitch.test.ModuleRules;
import org.junit.jupiter.api.Test;

class ModuleStructureTest {

    @Test
    void modulesFollowTheRules() {
        ModuleRules.verify("com.example.shop");
    }
}
```

It fails with a list of every problem, for example:

```
Module rules violated (2):
 - com.example.shop.catalog.api.CatalogApi overloads method 'find'. Module APIs may not overload methods; give each variant its own name.
 - com.example.shop.orders.impl.OrdersService (module 'orders') uses com.example.shop.catalog.impl.CatalogService
   from the implementation of module 'catalog'. Depend on that module's API instead.
```

Use `ModuleRules.of("com.example.shop").violations()` if you want the list instead of an assertion.

---

## 11. Split a module into its own service

Say catalog needs its own server. You deploy **the same code twice**, with different configuration.

### 11.1 The new catalog service

```yaml
# catalog deployment
module-switch:
  modules:
    catalog:
      mode: local
  expose:
    port: 8081           # internal port for other deployments
    modules: [catalog]   # served under /catalog
```

orders isn't listed, so it is off here and its classes are never loaded.

If catalog itself calls another module, set that module to `http` here too, pointing at the deployment that runs it.

### 11.2 The monolith

```yaml
# monolith deployment
module-switch:
  modules:
    orders:
      mode: local
    catalog:
      mode: http
      url: http://catalog-service:8081
```

That's all. `OrdersService` still injects `CatalogApi` and calls `getProduct(...)`, but now the call goes over HTTP. `CatalogService` is not loaded in the monolith at all.

### 11.3 What happens on the wire

You never write this yourself, but it helps when debugging:

```
POST http://catalog-service:8081/catalog/CatalogApi/getProduct
Content-Type: application/json

{"id": 42}
```

- Success: `200` with the result as JSON, or `204` for `void` methods and `null` results.
- Error: the matching status with an `application/problem+json` body:

```json
{ "type": "about:blank", "title": "NotFound", "status": 404,
  "detail": "Product 42 not found", "exception": "NotFound",
  "code": "PRODUCT_NOT_FOUND", "details": { "id": 42 } }
```

The internal port is separate from your application's normal port (for example 8080). Your web filters and Spring Security do **not** run on it.

### 11.4 Going back

To bring catalog back into the monolith, set `catalog: { mode: local }` again and stop the catalog deployment. No code changes.

---

## 12. Secure and tune remote calls

### 12.1 Protect the internal port

The internal port is open by default, so you can protect it with a firewall or Kubernetes network policy. To also require a shared secret:

```yaml
# catalog deployment
module-switch:
  expose:
    token: ${INTERNAL_TOKEN}
```

```yaml
# monolith deployment
module-switch:
  modules:
    catalog:
      mode: http
      url: http://catalog-service:8081
      token: ${INTERNAL_TOKEN}
```

The client sends it as the `X-Module-Switch-Token` header. A missing or wrong token gets `ModuleException.Unauthorized`.

### 12.2 Forward the user's identity

By default the `Authorization` header of the incoming web request is forwarded on every remote module call. Change the list if you need more:

```yaml
module-switch:
  propagate-headers: [Authorization, X-Tenant-Id]
```

On the serving side the internal port runs no web filters, so read forwarded headers with `ModuleCallContext`:

```java
import io.github.ilyaslabs.moduleswitch.core.ModuleCallContext;

String auth = ModuleCallContext.current()
        .flatMap(ctx -> ctx.header("Authorization"))
        .orElse(null);
```

`ModuleCallContext.current()` is empty when the module is called locally. Headers are forwarded again if catalog calls a further remote module.

### 12.3 Timeouts and retries

```yaml
module-switch:
  modules:
    catalog:
      mode: http
      url: http://catalog-service:8081
      connect-timeout: 2s       # default 2s
      read-timeout: 10s         # default 10s
      retry:
        max-attempts: 3         # default 3; 1 turns retries off
        initial-backoff: 100ms  # doubles each retry, with random jitter
```

Retries are deliberately careful, so an order is never placed twice:

| What went wrong | Normal method | `@Idempotent` method |
|---|---|---|
| Connection refused, unknown host, connect timeout | retried | retried |
| Server answered "not ready" (starting or shutting down) | retried | retried |
| No answer in time (read timeout), connection dropped mid-call | **not** retried | retried |
| 502, 503, 504 from a proxy | **not** retried | retried |
| Any `ModuleException` from the module itself | not retried | not retried |

When retries run out, the caller gets `ModuleException.Unavailable` with code `MODULE_UNAVAILABLE`.

---

## 13. Monitor module calls

If your application has Micrometer (for example via `spring-boot-starter-actuator`), every module call records an observation named `module.switch.call`, as both a metric and a trace span, tagged with:

| Tag | Example |
|---|---|
| `module` | `catalog` |
| `interface` | `CatalogApi` |
| `method` | `getProduct` |
| `mode` | `local` or `http` |
| `outcome` | `success`, `NotFound`, `Internal`, ... |

Because the names are the same in both modes, one Grafana dashboard shows a module's traffic before and after you split it. When tracing is set up (for example with Micrometer Tracing), trace context is forwarded automatically, so a request shows up as one trace across services.

---

## 14. Move a module to its own repository

Later you may want catalog's code out of the monolith's repository too. This works without any library change.

1. **Move `catalog-api` and `catalog-impl` to the new repository** and publish both as versioned jars (Maven Central or your private repository).
2. **In the monolith, depend on the published `catalog-api` jar** instead of the local module, and set `catalog: { mode: http, url: ... }`.
3. **In the catalog repository, add a small Spring Boot application** with the starter, `catalog-impl`, and `catalog: { mode: local }` plus `expose`. If catalog calls other modules, depend on their published `-api` jars and set them to `http`.
4. **Want both ways?** If the monolith also depends on the published `catalog-impl` jar, it can switch back to `catalog: local` with a config change.

Two things to keep in mind:

- **Keep the same root package** (for example `com.example.shop.catalog`) in both repositories. Module-Switch and Spring look under the main class's package. If the root package differs, add it to `module-switch.base-packages`, and add it to your component scan for the local case.
- **Evolve `catalog-api` compatibly.** Adding methods and adding fields is safe. Renaming the interface, a method or a `@Param` name breaks callers on the old version, because those names form the URL and the JSON. Unknown JSON fields are ignored and missing ones arrive as `null` (or `0` for numbers), so mixed versions keep working during a rollout.

---

## 15. Startup errors and how to fix them

Module-Switch checks everything at startup and lists **all** problems at once, under `APPLICATION FAILED TO START`.

| Message starts with | Cause | Fix |
|---|---|---|
| `Unknown module 'catalgo'` | A typo in `module-switch.modules` or `expose.modules` | Use one of the names listed in the message |
| `Module 'catalog' is http but has no url` | `mode: http` without `url` | Add `url: http://host:port` |
| `... is listed in module-switch.expose.modules but is http/off` | Only `local` modules can be exposed | Set the module to `local`, or remove it from `expose.modules` |
| `module-switch.expose.modules is set but module-switch.expose.port is not` | No internal port | Add `expose.port` |
| `OrdersService needs CatalogApi, but module 'catalog' is off` | A loaded class injects an `off` module's API | Set catalog to `local` or `http` |
| `... overloads method '...'` | Two methods with the same name in an API | Rename one of them |
| `... has no @Param` | A parameter without `@Param` | Add `@Param("name")` |
| `Bean '...' belongs to module 'catalog', which is http, but was registered anyway` | Your own `@ComponentScan`, `@Import`, `@Bean` or `@Enable...Repositories` reaches into a module from outside | Move it into the module's package (see [6.3](#63-repositories-and-entities-stay-inside-the-module)) |
| `... uses ... from the implementation of module 'catalog'` | Code depends on another module's implementation class | Depend on that module's API interface instead |
| `Module 'catalog' is local but no bean implements CatalogApi` | The implementation is missing or isn't a bean | Add `@Service` to the implementation, or check its package |
| `Module 'catalog' has 2 beans implementing CatalogApi` | Two implementations | Keep exactly one |

---

## 16. Rules checklist

Before you open a pull request:

- [ ] Every module has `package-info.java` with `@ModuleApi` on its API package and `@Module` on its implementation package.
- [ ] API packages contain only interfaces and plain data classes (records); data interfaces are `sealed`.
- [ ] Every API parameter has `@Param("name")`; no API method is overloaded.
- [ ] Modules use each other only through API interfaces.
- [ ] Errors are thrown as `ModuleException` subtypes.
- [ ] `@Enable...Repositories` and `@EntityScan` live in a config class inside the module, never on the main class.
- [ ] No custom `@ComponentScan` or `@Import` reaches into a module's packages.
- [ ] `ModuleRules.verify("your.root.package")` passes.

---

## 17. Configuration reference

```yaml
module-switch:
  modules:
    <module-name>:
      mode: off                     # local | http | off (default off)
      url: http://host:8081         # required when mode is http
      token: ...                    # sent as X-Module-Switch-Token (http mode)
      connect-timeout: 2s           # http mode
      read-timeout: 10s             # http mode
      retry:
        max-attempts: 3             # 1 disables retries
        initial-backoff: 100ms      # doubles each retry, with jitter
  expose:
    port: 8081                      # internal port; unset = nothing exposed; 0 = any free port
    modules: [<module-name>]        # must be local; each served under /<module-name>
    token: ...                      # when set, required on every incoming call
  propagate-headers: [Authorization]  # incoming headers forwarded on remote calls
  base-packages: []                 # extra packages to search for modules
```
