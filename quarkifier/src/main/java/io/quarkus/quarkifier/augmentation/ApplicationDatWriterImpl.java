package io.quarkus.quarkifier.augmentation;

import io.quarkus.bootstrap.runner.SerializedApplication;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Implementation calling the 5-arg {@code SerializedApplication.write()} signature.
 *
 * <p>In 3.33, the deprecated {@code nonExistentSourcePaths} parameter was removed. Quarkus 4.0
 * added a 6-arg overload with {@code cracEnabled}; the 5-arg form delegates to it with {@code
 * false}, matching the default when CRaC is not enabled.
 */
public final class ApplicationDatWriterImpl implements ApplicationDatWriter {

  @Override
  public void write(
      OutputStream os,
      String mainClass,
      Path applicationRoot,
      List<Path> classPath,
      List<Path> parentFirst)
      throws IOException {
    SerializedApplication.write(os, mainClass, applicationRoot, classPath, parentFirst);
  }
}
