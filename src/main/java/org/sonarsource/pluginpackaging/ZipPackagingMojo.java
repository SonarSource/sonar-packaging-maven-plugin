/*
 * SonarQube Server :: Packaging Maven Plugin
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.pluginpackaging;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.util.DirectoryScanner;

import static java.lang.String.format;

/**
 * Builds a ZIP-packaged plugin artifact, with layout:
 * <pre>
 * my-plugin-1.0.zip
 * ├── resources/   (content of {@link #resourcesDirectory}, any layout; should be excluded from the JAR, a warning is logged otherwise)
 * └── plugin/
 *     └── my-plugin-1.0.jar   (the JAR found at this point in the build, manifest unchanged)
 * </pre>
 * This goal is <b>not</b> wired into this plugin's custom lifecycle mapping and never runs implicitly: it only
 * builds the ZIP when explicitly bound via an {@code <execution>} in the consuming project's own {@code pom.xml},
 * typically to the {@code package} phase. Binding this goal is itself the opt-in: there is no separate flag to
 * enable or disable it. {@link #resourcesDirectory} is best configured inside that {@code <execution>}'s own
 * {@code <configuration>} rather than the plugin's top-level one, since the latter also applies to the
 * {@code check} and {@code sonar-plugin} goals, which don't have this parameter.
 * <p>
 * <b>Ordering requirement:</b> this goal wraps whatever file is found at the plugin JAR's expected path at the
 * time it runs. The plugin JAR itself is produced by the {@code sonar-plugin} goal (see {@link SonarPluginMojo}),
 * which always runs first within the {@code package} phase. If another {@code package}-phase plugin execution
 * rewrites that same JAR in place afterwards &mdash; most commonly {@code maven-shade-plugin}, to shade/minimize
 * dependencies into it &mdash; this goal's own {@code <execution>} must be declared <i>after</i> that plugin's
 * execution in the consuming {@code pom.xml}, so that it runs last within the {@code package} phase and therefore
 * picks up the final JAR rather than an intermediate one.
 */
@Mojo(name = "zip", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class ZipPackagingMojo extends AbstractSonarMojo {

  private static final String[] DEFAULT_EXCLUDES = {"**/package.html"};
  private static final String[] DEFAULT_INCLUDES = {"**/**"};
  private static final String ZIP_PLUGIN_ENTRY_PREFIX = "plugin/";
  private static final String ZIP_RESOURCES_ENTRY_PREFIX = "resources/";

  @Component(role = org.codehaus.plexus.archiver.Archiver.class, hint = "zip")
  protected ZipArchiver zipArchiver;

  /**
   * Directory whose content is copied verbatim under {@code resources/} in the ZIP artifact, using the default
   * include/exclude patterns. Defaults to this plugin ecosystem's existing "static resources" convention
   * ({@code src/main/resources/static}) so migrating authors don't need to move files. This content is not
   * automatically excluded from the plugin JAR. If it turns out to also be packaged in the JAR (which happens by
   * default, since the JAR packaging includes it unless explicitly excluded), a build warning is logged
   * recommending it be moved out of the JAR to avoid shipping the same resources in both artifacts. If the
   * directory does not exist, the produced ZIP simply has no {@code resources/} entries.
   */
  @Parameter(property = "sonar.resourcesDirectory", defaultValue = "${project.basedir}/src/main/resources/static")
  private File resourcesDirectory;

  @Override
  public void execute() throws MojoExecutionException {
    File jarFile = getJarFile(getOutputDirectory(), getFinalName(), getClassifier());
    if (!jarFile.isFile()) {
      throw new MojoExecutionException(format("Cannot build zip artifact: plugin JAR not found at '%s'. "
        + "Make sure the 'zip' goal's execution is declared after the 'sonar-plugin' goal (and after any other "
        + "package-phase plugin execution, e.g. maven-shade-plugin, that produces the final JAR) in this pom.xml.",
        jarFile));
    }
    warnIfResourcesDirectoryDuplicatedInJar(jarFile);
    File zipFile = createZipArchive(jarFile);
    projectHelper.attachArtifact(getProject(), "zip", getClassifier(), zipFile);
  }

  /**
   * Warns when {@link #resourcesDirectory}'s content is also found in the plugin JAR, under the
   * {@code <resourcesDirectory name>/<relative path>} entry that this plugin ecosystem's "static resources"
   * convention produces (e.g. {@code static/models/sample.txt}). This is only a naming-convention heuristic: it
   * does not detect duplication for a {@link #resourcesDirectory} laid out differently in the JAR.
   */
  private void warnIfResourcesDirectoryDuplicatedInJar(File jarFile) {
    if (!resourcesDirectory.isDirectory()) {
      return;
    }
    DirectoryScanner scanner = new DirectoryScanner();
    scanner.setBasedir(resourcesDirectory);
    scanner.setIncludes(DEFAULT_INCLUDES);
    scanner.setExcludes(DEFAULT_EXCLUDES);
    scanner.scan();
    try (ZipFile jar = new ZipFile(jarFile)) {
      for (String resourceFile : scanner.getIncludedFiles()) {
        String jarEntryName = resourcesDirectory.getName() + "/" + resourceFile.replace(File.separatorChar, '/');
        if (jar.getEntry(jarEntryName) != null) {
          getLog().warn(format("Resources directory '%s' is also packaged inside the plugin JAR: its content is "
            + "included twice, in both the JAR and the ZIP's 'resources/' folder. Those resources should be moved "
            + "out of the JAR packaging (e.g. via this plugin's 'excludes' parameter) to avoid the duplication.",
            resourcesDirectory));
          return;
        }
      }
    } catch (IOException e) {
      getLog().debug(format("Could not inspect '%s' to check for resources duplicated in the plugin JAR", jarFile), e);
    }
  }

  private File createZipArchive(File jarFile) {
    File zipFile = getZipFile(getOutputDirectory(), getFinalName(), getClassifier());
    zipArchiver.setDestFile(zipFile);
    try {
      zipArchiver.addFile(jarFile, ZIP_PLUGIN_ENTRY_PREFIX + jarFile.getName());
      if (resourcesDirectory.isDirectory()) {
        zipArchiver.addDirectory(resourcesDirectory, ZIP_RESOURCES_ENTRY_PREFIX, DEFAULT_INCLUDES, DEFAULT_EXCLUDES);
      } else {
        getLog().debug(format("No resources directory found at '%s'; zip will have no 'resources/' entries", resourcesDirectory));
      }
      zipArchiver.createArchive();
      return zipFile;
    } catch (Exception e) {
      throw new IllegalStateException("Fail to build SonarQube plugin zip", e);
    }
  }
}
