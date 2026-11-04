# How to Contribute

## Using devcontainers

If you are using [devcontainers](https://code.visualstudio.com/docs/devcontainers/containers)
and/or [codespaces](https://github.com/features/codespaces) then you can start
contributing immediately and skip the environment setup.

## Developer On-Boarding

For building, testing, project structure, and working with the examples workspace, see the [Developer Guide](docs/developer-guide.md).

## Formatting

Starlark files should be formatted by buildifier.
We suggest using a pre-commit hook to automate this.
First [install pre-commit](https://pre-commit.com/#installation),
then run

```shell
pre-commit install
```

Otherwise later tooling on CI will yell at you about formatting/linting violations.

## Updating BUILD files

Some targets are generated from sources.
Currently this is just the `bzl_library` targets.
Run `bazel run //dev:gazelle` to keep them up-to-date.

## Using this as a development dependency of other rules

You'll commonly find that you develop in another WORKSPACE, such as
some other ruleset that depends on rules_quarkus, or in a nested
WORKSPACE in the integration_tests folder.

To always tell Bazel to use this directory rather than some release
artifact or a version fetched from the internet, run this from this
directory:

```sh
OVERRIDE="--override_repository=rules_quarkus=$(pwd)/rules_quarkus"
echo "common $OVERRIDE" >> ~/.bazelrc
```

This means that any usage of `@rules_quarkus` on your system will point to this folder.

## Releasing

Releases are cut by pushing a `vX.Y.Z` tag. Choose the version from the commit
history, which follows [Conventional Commits](https://www.conventionalcommits.org/):
a `fix` a patch release, a `feat` makes a minor release, and a breaking change
(`!`) a major release.

```sh
git fetch
git tag v1.2.3 origin/main
git push origin v1.2.3
```

The tag triggers `.github/workflows/release.yaml`, which builds the release
artifacts, creates the GitHub release, and opens a pull request on the
[Bazel Central Registry](https://registry.bazel.build/). Release candidates work
the same way, for example `v1.0.0-rc0`.

## Architecture Reference

For deeper understanding of the system design, see:
- [Architecture Overview](docs/architecture.md) — three-layer architecture, module extension, build flow
- [Quarkifier Tool Reference](docs/quarkifier.md) — CLI, pipeline, ApplicationModel construction
- [Dev Mode & Dev UI Integration](docs/dev-mode.md) — classloader isolation, subprocess approach, known bugs
