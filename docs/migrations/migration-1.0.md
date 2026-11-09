# Migrating from 0.x to 1.0

Version 1.0 publishes the rules under a new module name, `rules_quarkus`, and
moves the project to [quarkusio/quarkus-bazel-rules](https://github.com/quarkusio/quarkus-bazel-rules).
Releases `0.1.0` to `0.8.0` were published to the Bazel Central Registry as
`com_clementguillot_rules_quarkus`; that module receives no further releases.

The rules behave the same, apart from the new names and the supported Quarkus
versions: 1.0 supports the latest LTS patch releases, `3.33.4.1` and
`3.40.1.1`, while `0.8.0` supports `3.33.4` and `3.40.1`.

## What changed

| | 0.x | 1.0 |
|---|---|---|
| Module | `com_clementguillot_rules_quarkus` | `rules_quarkus` |
| Extension | `@com_clementguillot_rules_quarkus//quarkus:extensions.bzl` | `@rules_quarkus//quarkus:extensions.bzl` |
| Generated repository | `use_repo(quarkus, "rules_quarkus")` | `use_repo(quarkus, "quarkus")` |
| Public macros | `@rules_quarkus//quarkus:defs.bzl` | `@quarkus//:defs.bzl` |
| Other generated targets | `@rules_quarkus//deployment:all`, ... | `@quarkus//deployment:all`, ... |
| Module files (providers, rules) | `@com_clementguillot_rules_quarkus//quarkus:providers.bzl`, ... | `@rules_quarkus//quarkus:providers.bzl`, ... |

The generated repository had to be renamed: `rules_quarkus` is now the name of
the module itself.

## 1. Update MODULE.bazel

Before:

```starlark
bazel_dep(name = "com_clementguillot_rules_quarkus", version = "0.8.0")

quarkus = use_extension(
    "@com_clementguillot_rules_quarkus//quarkus:extensions.bzl",
    "quarkus",
)
quarkus.toolchain(
    quarkus_version = "3.40.1",
    lock_file = "//:maven_install.json",
)
use_repo(quarkus, "rules_quarkus")
```

After:

```starlark
bazel_dep(name = "rules_quarkus", version = "1.0.0")

quarkus = use_extension(
    "@rules_quarkus//quarkus:extensions.bzl",
    "quarkus",
)
quarkus.toolchain(
    quarkus_version = "3.40.1.1",
    lock_file = "//:maven_install.json",
)
use_repo(quarkus, "quarkus")
```

Also rename the module in any `git_override`, `archive_override`, or
`local_path_override` (`module_name = "rules_quarkus"`), and in a
`quarkifier_source_dir` label (`@rules_quarkus//:MODULE.bazel`).

## 2. Move to the new Quarkus patch release

| `0.8.0` | 1.0 |
|---|---|
| `3.33.4` | `3.33.4.1` |
| `3.40.1` | `3.40.1.1` |

Use the new patch release everywhere the workspace names a Quarkus version:
`quarkus_version`, the platform BOM in `maven.install(boms = [...])`, and any
Quarkus artifact pinned with an explicit version. For example, replace
`io.quarkus.platform:quarkus-bom:3.40.1` with
`io.quarkus.platform:quarkus-bom:3.40.1.1`. Then re-pin the Maven lock file:

```bash
REPIN=1 bazel run @maven//:pin
```

## 3. Refresh the lockfile

Run any build, for example `bazel build //...`, to update `MODULE.bazel.lock`
for the new module name.

## Troubleshooting

A partial migration fails with errors that do not mention the rename:

- **`Error in use_repo: The repo name 'rules_quarkus' is already being used by a bazel_dep`**:
  `MODULE.bazel` still has `use_repo(quarkus, "rules_quarkus")`. Change it to
  `use_repo(quarkus, "quarkus")`.
- **`file '@rules_quarkus//quarkus:defs.bzl' does not contain symbol 'quarkus_app'`**:
  a BUILD file still loads the macros from the 0.x path. Load them from
  `@quarkus//:defs.bzl`. `@rules_quarkus//quarkus:defs.bzl` now resolves to the
  module's internal rules, which is why the file exists.
- **`no such attribute 'srcs' in 'quarkus_test' rule`**: same cause as above, for
  a file that loads only `quarkus_test` or `quarkus_integration_test`; those
  names also exist among the internal rules, without the macro attributes.
- **`No repository visible as '@com_clementguillot_rules_quarkus'`**: a label
  still uses the old module name. Replace it with `@rules_quarkus`.
- **`Unsupported Quarkus version '3.40.1': the quarkifier is coupled to exact patch versions`**:
  `quarkus_version` still names a version that only `0.8.0` supports. Follow
  step 2.
