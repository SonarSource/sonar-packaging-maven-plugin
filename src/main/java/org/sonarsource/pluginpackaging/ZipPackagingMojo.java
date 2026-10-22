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
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
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
 * This goal runs in its own, dedicated module &mdash; separate from the one that builds the plugin JAR &mdash;
 * whose only job is building this ZIP. That module must declare exactly one dependency of type
 * {@code sonar-plugin} (pointing at the plugin module) and nothing else of that type; this goal wraps that
 * dependency's resolved JAR file, whatever it ends up being after the plugin module's own build &mdash;
 * including any {@code package}-phase plugin that rewrites the JAR in place there, e.g.
 * {@code maven-shade-plugin} &mdash; is completely finished. Maven's reactor guarantees that happens before
 * this module even starts, so there is no phase- or execution-ordering concern to manage here at all.
 * <p>
 * With {@code <packaging>sonar-plugin-zip</packaging>} on that dedicated module, this goal runs implicitly in
 * the {@code package} phase, with no {@code <execution>} needed: the built ZIP becomes the module's primary
 * artifact (unless a {@code classifier} is set, in which case it's attached instead, same as
 * {@link SonarPluginMojo}'s JAR). {@link #resourcesDirectory} can simply be set in this plugin's top-level
 * {@code <configuration>}.
 */
@Mojo(name = "zip", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
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
   * ({@code src/main/resources/static}), resolved against <i>this</i> module's own {@code basedir} &mdash; not
   * the plugin module's. Moving existing static resources into the ZIP means moving those files into this
   * module (or pointing this parameter back at the plugin module's own directory, if the plugin module should
   * keep serving them too). This content is not automatically excluded from the plugin module's JAR: nothing
   * links the two by default, since they're separate modules. If it turns out to also be present in the JAR
   * (e.g. because this parameter was pointed at the plugin module's own resources), a build warning is logged
   * recommending it be excluded from the JAR, in the plugin module, to avoid shipping the same resources in
   * both artifacts. If the directory does not exist, the produced ZIP simply has no {@code resources/} entries.
   */
  @Parameter(property = "sonar.resourcesDirectory", defaultValue = "${project.basedir}/src/main/resources/static")
  private File resourcesDirectory;

  @Override
  public void execute() throws MojoExecutionException {
    File jarFile = getSonarPluginDependency().getFile();
    if (jarFile == null || !jarFile.isFile()) {
      throw new MojoExecutionException(format("Cannot build zip artifact: the '%s'-typed dependency has no "
        + "resolved JAR file ('%s'). Make sure that module was built before this one, e.g. by running the "
        + "reactor build from the root.", SONAR_PLUGIN_TYPE, jarFile));
    }
    warnIfResourcesDirectoryDuplicatedInJar(jarFile);
    File zipFile = createZipArchive(jarFile);
    String classifier = getClassifier();
    if (classifier != null) {
      projectHelper.attachArtifact(getProject(), "zip", classifier, zipFile);
    } else {
      getProject().getArtifact().setFile(zipFile);
    }
  }

  /**
   * Finds this module's single dependency of type {@code sonar-plugin} &mdash; the plugin module whose JAR
   * this goal wraps into the ZIP. Fails if there isn't exactly one, since there would otherwise be no
   * unambiguous way to pick which one to wrap.
   */
  private Artifact getSonarPluginDependency() throws MojoExecutionException {
    Set<Artifact> sonarPluginDependencies = new HashSet<>();
    for (Artifact dependency : getDependencyArtifacts()) {
      if (SONAR_PLUGIN_TYPE.equals(dependency.getType())) {
        sonarPluginDependencies.add(dependency);
      }
    }
    if (sonarPluginDependencies.size() != 1) {
      throw new MojoExecutionException(format("This module must declare exactly one dependency of type '%s' "
        + "(the plugin module whose JAR this goal wraps into the ZIP): found %d. Declare a <dependency> on "
        + "your plugin module with <type>%s</type>, and nothing else of that type.",
        SONAR_PLUGIN_TYPE, sonarPluginDependencies.size(), SONAR_PLUGIN_TYPE));
    }
    return sonarPluginDependencies.iterator().next();
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
          getLog().warn(format("Resources directory '%s' is also packaged inside the plugin JAR ('%s'): its "
            + "content is included twice, in both the JAR and the ZIP's 'resources/' folder. Remove those "
            + "resources from the plugin module (or exclude them there via the 'sonar-plugin' goal's "
            + "'excludes' parameter) to avoid the duplication.",
            resourcesDirectory, jarFile));
          return;
        }
      }
    } catch (IOException e) {
      getLog().debug(format("Could not inspect '%s' to check for resources duplicated in the plugin JAR", jarFile), e);
    }
  }

  private static File getZipFile(File basedir, String finalName, String classifier) {
    return new File(basedir, finalName + getClassifierSuffix(classifier) + ".zip");
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
