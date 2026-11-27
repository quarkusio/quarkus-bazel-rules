package io.quarkus.quarkifier.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.quarkifier.model.transport.BazelApplicationModel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Cross-version serialization tests for the explicit Quarkus model adapter. */
class ExplicitApplicationModelBuilderTest {

  @Test
  void devModelUsesStableArtifactPathWhenBazelExecrootSymlinkDisappears(@TempDir Path tempDir)
      throws Exception {
    Path repository = Files.createDirectories(tempDir.resolve("output-base/external/repository"));
    Path backingJar = repository.resolve("dependency.jar");
    try (var ignored = new JarOutputStream(Files.newOutputStream(backingJar))) {
      // Empty but valid JAR.
    }
    Path execroot = Files.createDirectories(tempDir.resolve("execroot/workspace"));
    Path externalLink = execroot.resolve("external");
    Files.createSymbolicLink(externalLink, tempDir.resolve("output-base/external"));
    Path logicalJar = externalLink.resolve("repository/dependency.jar");

    var application =
        new BazelApplicationModel.Node(
            "app",
            BazelApplicationModel.NodeKind.APPLICATION,
            new BazelApplicationModel.ArtifactCoordinates("test", "app", "", "jar", "1.0"),
            List.of(logicalJar.toString()),
            List.of(),
            new BazelApplicationModel.ClasspathFacts(
                false, true, false, false, false, false, false),
            null,
            "//:app");
    var model =
        new BazelApplicationModel(
            BazelApplicationModel.SCHEMA_VERSION,
            new BazelApplicationModel.Producer("test", "1"),
            io.quarkus.quarkifier.QuarkifierVersionProvider.targetedQuarkusVersion(),
            BazelApplicationModel.Mode.DEV,
            application.id(),
            List.of(application),
            List.of(),
            new BazelApplicationModel.Platform(List.of(), Map.of(), List.of()));

    var built = ExplicitApplicationModelBuilder.build(model);
    Path resolved = built.getAppArtifact().getResolvedPaths().getSinglePath();
    Files.delete(externalLink);

    assertEquals(backingJar.toRealPath(), resolved);
    assertTrue(Files.exists(resolved), "resolved model path must survive execroot symlink removal");
  }

  @Test
  void workspaceSourceMetadataSurvivesNativeModelSerialization(@TempDir Path tempDir)
      throws Exception {
    Path appJar = tempDir.resolve("app.jar");
    try (var ignored = new JarOutputStream(Files.newOutputStream(appJar))) {
      // An empty, valid application jar is sufficient for model construction.
    }
    Path sourceDir = Files.createDirectories(tempDir.resolve("src/main/java"));
    Path serialized = tempDir.resolve("application-model.dat");

    var sourceSet =
        new BazelApplicationModel.SourceSet(
            "",
            List.of(sourceDir.toString()),
            List.of(),
            List.of(appJar.toString()),
            List.of(),
            List.of());
    var workspace =
        new BazelApplicationModel.WorkspaceModule(
            "workspace",
            "//:app",
            tempDir.toString(),
            tempDir.resolve("bazel-bin").toString(),
            tempDir.resolve("BUILD.bazel").toString(),
            List.of(sourceSet),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null);
    var application =
        new BazelApplicationModel.Node(
            "app",
            BazelApplicationModel.NodeKind.APPLICATION,
            new BazelApplicationModel.ArtifactCoordinates("test", "app", "", "jar", "1.0"),
            List.of(appJar.toString()),
            List.of(),
            new BazelApplicationModel.ClasspathFacts(
                false, false, false, false, false, false, false),
            workspace.id(),
            "//:app");
    var model =
        new BazelApplicationModel(
            BazelApplicationModel.SCHEMA_VERSION,
            new BazelApplicationModel.Producer("test", "1"),
            io.quarkus.quarkifier.QuarkifierVersionProvider.targetedQuarkusVersion(),
            BazelApplicationModel.Mode.NORMAL,
            application.id(),
            List.of(application),
            List.of(workspace),
            new BazelApplicationModel.Platform(List.of(), Map.of(), List.of()));

    QuarkusModelVersionAdapter.serialize(ExplicitApplicationModelBuilder.build(model), serialized);

    assertTrue(Files.size(serialized) > 0);
    var roundTripped = QuarkusModelVersionAdapter.deserialize(serialized);
    assertEquals("test:app::jar:1.0", roundTripped.getAppArtifact().toGACTVString());
    assertEquals(
        "io.quarkus.bootstrap.workspace.LazySourceDir",
        roundTripped
            .getAppArtifact()
            .getWorkspaceModule()
            .getMainSources()
            .getSourceDirs()
            .iterator()
            .next()
            .getClass()
            .getName());
  }

  @Test
  void requiredQuarkusVersionFromExtensionDescriptorSurvivesSerialization(@TempDir Path tempDir)
      throws Exception {
    Path appJar = tempDir.resolve("app.jar");
    try (var ignored = new JarOutputStream(Files.newOutputStream(appJar))) {
      // An empty, valid application jar is sufficient for model construction.
    }
    Path extensionJar = tempDir.resolve("ext.jar");
    try (var jar = new JarOutputStream(Files.newOutputStream(extensionJar))) {
      jar.putNextEntry(new JarEntry("META-INF/quarkus-extension.properties"));
      jar.write(
          ("deployment-artifact=test\\:ext-deployment\\:1.0\n"
                  + "requires-quarkus-version=[4.0,5)\n")
              .getBytes(StandardCharsets.UTF_8));
      jar.closeEntry();
    }
    Path deploymentJar = tempDir.resolve("ext-deployment.jar");
    try (var ignored = new JarOutputStream(Files.newOutputStream(deploymentJar))) {
      // The deployment artifact only needs to exist.
    }
    Path serialized = tempDir.resolve("application-model.dat");

    var deployment =
        new BazelApplicationModel.Node(
            "ext-deployment",
            BazelApplicationModel.NodeKind.DEPLOYMENT,
            new BazelApplicationModel.ArtifactCoordinates(
                "test", "ext-deployment", "", "jar", "1.0"),
            List.of(deploymentJar.toString()),
            List.of(),
            new BazelApplicationModel.ClasspathFacts(
                false, false, true, false, false, false, false),
            null,
            null);
    var extension =
        new BazelApplicationModel.Node(
            "ext",
            BazelApplicationModel.NodeKind.MAVEN,
            new BazelApplicationModel.ArtifactCoordinates("test", "ext", "", "jar", "1.0"),
            List.of(extensionJar.toString()),
            List.of(
                new BazelApplicationModel.DependencyEdge(
                    deployment.id(),
                    BazelApplicationModel.DependencyRelation.DEPLOYMENT,
                    BazelApplicationModel.DependencyScope.COMPILE,
                    false,
                    List.of())),
            new BazelApplicationModel.ClasspathFacts(true, true, true, false, false, false, true),
            null,
            null);
    var application =
        new BazelApplicationModel.Node(
            "app",
            BazelApplicationModel.NodeKind.APPLICATION,
            new BazelApplicationModel.ArtifactCoordinates("test", "app", "", "jar", "1.0"),
            List.of(appJar.toString()),
            List.of(
                new BazelApplicationModel.DependencyEdge(
                    extension.id(),
                    BazelApplicationModel.DependencyRelation.DEPS,
                    BazelApplicationModel.DependencyScope.COMPILE,
                    false,
                    List.of())),
            new BazelApplicationModel.ClasspathFacts(
                false, false, false, false, false, false, false),
            null,
            "//:app");
    var model =
        new BazelApplicationModel(
            BazelApplicationModel.SCHEMA_VERSION,
            new BazelApplicationModel.Producer("test", "1"),
            io.quarkus.quarkifier.QuarkifierVersionProvider.targetedQuarkusVersion(),
            BazelApplicationModel.Mode.NORMAL,
            application.id(),
            List.of(application, extension, deployment),
            List.of(),
            new BazelApplicationModel.Platform(List.of(), Map.of(), List.of()));

    var built = ExplicitApplicationModelBuilder.build(model);
    ArtifactKey extensionKey = ArtifactKey.of("test", "ext", "", "jar");
    assertEquals(Map.of(extensionKey, "[4.0,5)"), built.getRequiredQuarkusVersions());

    QuarkusModelVersionAdapter.serialize(built, serialized);
    var roundTripped = QuarkusModelVersionAdapter.deserialize(serialized);
    assertEquals(Map.of(extensionKey, "[4.0,5)"), roundTripped.getRequiredQuarkusVersions());
  }
}
