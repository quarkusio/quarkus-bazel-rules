# Migrating from 1.0 to 2.0

Version 2.0 moves rules_quarkus to Quarkus 4. It supports Quarkus `4.0.1` and
requires Java 21; Quarkus 3.x is no longer supported on this line.

The rules, macros, and module names are unchanged. Apart from the Quarkus and
Java versions, an application needs one new `maven.install` setting (step 3).

## Staying on Quarkus 3.x

rules_quarkus 1.0.x keeps supporting Quarkus `3.33.4.2` and `3.40.1.2` LTS. It
is maintained on the [`1.0` branch](https://github.com/quarkusio/quarkus-bazel-rules/tree/1.0)
with bug fixes and LTS patch bumps until the Quarkus 3.33 and 3.40 LTS end of
life. Keep `bazel_dep(name = "rules_quarkus", version = "1.0.x")` to stay there.

2.0 raises the module's `compatibility_level` to 2. If another module in your
dependency graph requires rules_quarkus 2.x while yours requires 1.0.x, Bazel
fails with an incompatible-versions error instead of silently moving your
Quarkus 3 application to a release that does not support it.

## What changed

| | 1.0.x | 2.0 |
|---|---|---|
| Quarkus | `3.33.4.2`, `3.40.1.2` | `4.0.1` |
| Java | 17+ | 21+ |
| `compatibility_level` | 0 | 2 |
| GraalVM (native) | 25+ | 25+ |

## 1. Use Java 21

Quarkus 4 is compiled for Java 21. Point the Bazel Java flags at 21 or newer,
for example in `.bazelrc`:

```
common --java_language_version=21
common --tool_java_language_version=21
common --java_runtime_version=remotejdk_21
common --tool_java_runtime_version=remotejdk_21
```

The module extension runs Coursier and the quarkifier during repository fetch,
before toolchain resolution. It uses `JAVA_HOME`, then `java` on `PATH`, when
they are Java 21 or newer, and otherwise falls back to a hermetic remote JDK 21.

## 2. Move to Quarkus 4.0.1

Update `MODULE.bazel`:

```starlark
bazel_dep(name = "rules_quarkus", version = "2.0.0")

quarkus = use_extension("@rules_quarkus//quarkus:extensions.bzl", "quarkus")
quarkus.toolchain(
    quarkus_version = "4.0.1",
    lock_file = "//:maven_install.json",
)
use_repo(quarkus, "quarkus")
```

Use `4.0.1` everywhere the workspace names a Quarkus version: the platform BOM
in `maven.install(boms = [...])` and any Quarkus artifact pinned with an
explicit version.

Quarkus 4 itself has breaking changes (Vert.x 5, Jackson 3, Hibernate ORM 8,
`quarkus-test-spring` renamed to `quarkus-spring-test`, and more). Follow the
Quarkus 4.0 migration guide for application code and configuration.

Two of them show up in local extensions built with these rules:

- `CodeGenProvider.inputExtension()` was removed. A custom code generator used
  by `quarkus_codegen` must implement `String[] inputExtensions()` instead.
- `CodeGenContext.config()` now returns `io.smallrye.config.Config` instead of
  MicroProfile `Config`.

## 3. Exclude the relocated CDI 4 coordinates

Add `excluded_artifacts` to the `maven.install` that declares your Quarkus
dependencies:

```starlark
maven.install(
    artifacts = [
        "io.quarkus:quarkus-rest",
        "io.quarkus:quarkus-arc",
    ],
    boms = ["io.quarkus.platform:quarkus-bom:4.0.1"],
    excluded_artifacts = [
        "jakarta.enterprise:jakarta.enterprise.cdi-api",
        "jakarta.enterprise:jakarta.enterprise.lang-model",
    ],
    lock_file = "//:maven_install.json",
)
```

CDI 5.0 moved from `jakarta.enterprise` to `jakarta.cdi` coordinates, and many
libraries still depend on the old names. The parent POM published for the old
names at 5.0.0 relocates to an artifact that does not exist on Maven Central.
Maven ignores relocations on parent POMs, but Coursier (used by
`rules_jvm_external` and by rules_quarkus) follows it and fails. Quarkus already
depends on `jakarta.cdi:jakarta.cdi-api` and `jakarta.cdi:jakarta.cdi-lang-model-api`
directly, so excluding the old names leaves the same classpath. rules_quarkus
applies the same exclusion to its own deployment and conditional resolution.

BUILD files that depend on the CDI API directly must use the new target. The
Java packages (`jakarta.enterprise.*`) are unchanged in CDI 5:

```starlark
# Before
deps = ["@maven//:jakarta_enterprise_jakarta_enterprise_cdi_api"]
# After
deps = ["@maven//:jakarta_cdi_jakarta_cdi_api"]
```

## 4. Re-pin and rebuild

```bash
REPIN=1 bazel run @maven//:pin
bazel build //...
```

## Troubleshooting

- **`Resolution error: Error downloading jakarta.cdi:jakarta.cdi.parent:5.0.0`**:
  the `maven.install` is missing the exclusions from step 3.
- **`no such target '@maven//:jakarta_enterprise_jakarta_enterprise_cdi_api'`**:
  a BUILD file still uses the CDI 4 target. Use `@maven//:jakarta_cdi_jakarta_cdi_api`
  (step 3).
- **`Unsupported Quarkus minor version '3.40'`**: the workspace still names a
  Quarkus 3 version. Move to `4.0.1`, or stay on rules_quarkus 1.0.x.
- **`rules_quarkus needs Java 21 or newer to run the quarkifier`**: `JAVA_HOME`
  or `java` on `PATH` is older than 21 and no hermetic fallback exists for the
  host platform. Point `JAVA_HOME` at a Java 21+ JDK.
- **`UnsupportedClassVersionError ... class file version 65.0`**: a build or
  test action runs on Java 17. Apply the `.bazelrc` flags from step 1.
- **`NoSuchMethodError: ... registerBuildTimeIndyIncludeList` or
  `Class initialization of io.netty.handler.codec.compression.BrotliOptions failed`**
  in a `native = True` build: host native compilation has no working GraalVM
  distribution with Quarkus 4.0.1 yet. Use `native_container_build = True`, and
  see [Native Image: known issue](../native-image.md#known-issue-host-builds-fail-with-quarkus-401).
