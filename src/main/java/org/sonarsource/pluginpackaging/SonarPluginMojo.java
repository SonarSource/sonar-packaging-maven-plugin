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

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilderException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.util.FileUtils;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * Build a SonarQube Plugin from the current project.
 */
@Mojo(name = "sonar-plugin", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public class SonarPluginMojo extends AbstractSonarMojo {

  private static final DateTimeFormatter DATETIME_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
    .withZone(ZoneId.from(ZoneOffset.UTC));
  private static final String LIB_DIR = "META-INF/lib/";
  private static final String[] DEFAULT_EXCLUDES = new String[]{"**/package.html"};
  private static final String[] DEFAULT_INCLUDES = new String[]{"**/**"};
  private static final String STANDARD_RESOURCES_DIR = "src/main/resources";
  private static final String ZIP_PLUGIN_ENTRY_PREFIX = "plugin/";
  private static final String ZIP_RESOURCES_ENTRY_PREFIX = "resources/";

  /**
   * Name of the property needed to have reproducible builds.
   */
  private static final String REPRODUCIBLE_BUILDS_PROPERTY = "project.build.outputTimestamp";

  @Component(role = org.codehaus.plexus.archiver.Archiver.class, hint = "jar")
  protected JarArchiver jarArchiver;

  @Component(role = org.codehaus.plexus.archiver.Archiver.class, hint = "zip")
  protected ZipArchiver zipArchiver;

  /**
   * List of files to include. Specified as fileset patterns which are relative to the input directory whose contents
   * is being packaged into the JAR.
   */
  @Parameter
  private String[] includes;

  /**
   * List of files to exclude. Specified as fileset patterns which are relative to the input directory whose contents
   * is being packaged into the JAR.
   */
  @Parameter
  private String[] excludes;

  /**
   * Whether to additionally produce a ZIP-packaged plugin artifact, with layout:
   * <pre>
   * my-plugin-1.0.zip
   * ├── resources/   (content of {@link #resourcesDirectory}, any layout)
   * └── plugin/
   *     └── my-plugin-1.0.jar   (the JAR built by this same goal, byte-for-byte, manifest unchanged)
   * </pre>
   * The JAR packaging is always produced and remains the primary/default artifact; the ZIP, when enabled, is
   * attached as an additional artifact of type {@code zip}.
   */
  @Parameter(property = "sonar.zipPackaging", defaultValue = "false")
  private boolean zipPackaging;

  /**
   * Directory whose content is copied verbatim under {@code resources/} in the ZIP artifact. Only consulted when
   * {@link #zipPackaging} is {@code true}. Defaults to this plugin ecosystem's existing "static resources"
   * convention ({@code src/main/resources/static}) so migrating authors don't need to move files; when
   * {@link #zipPackaging} is {@code true}, this directory's content is also excluded from the JAR to avoid
   * duplicating it in both places. If it does not exist, the produced ZIP simply has no {@code resources/} entries.
   */
  @Parameter(property = "sonar.resourcesDirectory", defaultValue = "${project.basedir}/src/main/resources/static")
  private File resourcesDirectory;

  /**
   * The archive configuration to use.
   * See <a href="http://maven.apache.org/shared/maven-archiver/index.html">Maven Archiver Reference</a>.
   */
  @Parameter
  private final MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

  @Component
  private DependencyTreeBuilder dependencyTreeBuilder;

  @Parameter(defaultValue = "${localRepository}", readonly = true)
  private ArtifactRepository localRepository;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    checkPluginClass();
    checkSonarQubeMinVersionIsAbsent();
    File jarFile = createArchive();
    String classifier = getClassifier();
    if (classifier != null) {
      projectHelper.attachArtifact(getProject(), "jar", classifier, jarFile);
    } else {
      getProject().getArtifact().setFile(jarFile);
    }
    if (zipPackaging) {
      File zipFile = createZipArchive(jarFile);
      projectHelper.attachArtifact(getProject(), "zip", classifier, zipFile);
    }
  }

  private void checkSonarQubeMinVersionIsAbsent() throws MojoExecutionException {
    if (getSonarQubeMinVersion() != null) {
      throw new MojoExecutionException("'sonarQubeMinVersion' property cannot be specified anymore. " +
        "Please specify a valid minimum version of plugin api with 'pluginApiMinVersion' instead.");
    }
  }

  private void checkPluginClass() throws MojoExecutionException {
    String classname = getPluginClass();
    if (!new File(getClassesDirectory(), classname.replace('.', '/') + ".class").exists()) {
      throw new MojoExecutionException("Plugin class not found: '" + classname);
    }
  }

  public File createArchive() {
    File jarFile = getJarFile(getOutputDirectory(), getFinalName(), getClassifier());
    MavenArchiver archiver = new MavenArchiver();
    archiver.setArchiver(jarArchiver);
    archiver.setOutputFile(jarFile);

    try {
      archiver.getArchiver().addDirectory(getClassesDirectory(), getIncludes(), getJarExcludes());
      archive.setAddMavenDescriptor(isAddMavenDescriptor());

      String logLine = "-------------------------------------------------------";
      getLog().info(logLine);
      getLog().info("Plugin definition in update center");
      addManifestProperty(PluginManifestProperty.KEY, getPluginKey());
      addManifestProperty(PluginManifestProperty.NAME, getPluginName());
      addManifestProperty(PluginManifestProperty.DESCRIPTION, getPluginDescription());
      addManifestProperty(PluginManifestProperty.VERSION, getProject().getVersion());
      addManifestProperty(PluginManifestProperty.DISPLAY_VERSION, getPluginDisplayVersion());
      addManifestProperty(PluginManifestProperty.MAIN_CLASS, getPluginClass());
      addManifestProperty(PluginManifestProperty.REQUIRE_PLUGINS, getRequiredPlugins());
      addManifestProperty(PluginManifestProperty.SONARLINT_SUPPORTED, isSonarLintSupported());
      addManifestProperty(PluginManifestProperty.USE_CHILD_FIRST_CLASSLOADER, String.valueOf(isUseChildFirstClassLoader() != null && isUseChildFirstClassLoader()));
      addManifestProperty(PluginManifestProperty.BASE_PLUGIN, getBasePlugin());
      addManifestProperty(PluginManifestProperty.HOMEPAGE, getPluginUrl());
      addManifestProperty(PluginManifestProperty.SONAR_VERSION, getPluginApiMinVersion());
      addManifestProperty(PluginManifestProperty.LICENSE, getLicensing());
      addManifestProperty(PluginManifestProperty.ORGANIZATION, getPluginOrganizationName());
      addManifestProperty(PluginManifestProperty.ORGANIZATION_URL, getPluginOrganizationUrl());
      addManifestProperty(PluginManifestProperty.TERMS_CONDITIONS_URL, getPluginTermsConditionsUrl());
      addManifestProperty(PluginManifestProperty.ISSUE_TRACKER_URL, getPluginIssueTrackerUrl());
      addManifestProperty(PluginManifestProperty.BUILD_DATE, DATETIME_PATTERN.format(getBuildDate()));
      addManifestProperty(PluginManifestProperty.SOURCES_URL, getPluginSourcesUrl());
      addManifestProperty(PluginManifestProperty.DEVELOPERS, getDevelopers());
      addManifestProperty(PluginManifestProperty.JRE_MIN_VERSION, getJreMinVersion());
      addManifestProperty(PluginManifestProperty.NODEJS_MIN_VERSION, getNodeJsMinVersion());
      addManifestProperty(PluginManifestProperty.LANGUAGES, getRequiredForLanguages());
      if (isUseChildFirstClassLoader() != null) {
        getLog().warn("Property 'useChildFirstClassLoader' is deprecated");
      }
      if (isSkipDependenciesPackaging()) {
        getLog().info("Skip packaging of dependencies");

      } else {
        List<String> libs = copyDependencies();
        if (!libs.isEmpty()) {
          if (isSonarLintSupported()) {
            throw new MojoFailureException(
              "A SonarLint compatible plugin should not package dependencies. "
                + "Please set property 'skipDependenciesPackaging' to 'true' and use the maven-shade-plugin to shade instead.");
          }
          archiver.getArchiver().addDirectory(getAppDirectory(), getIncludes(), getExcludes());
          addManifestProperty(PluginManifestProperty.DEPENDENCIES, StringUtils.join(libs, " "));
        }
      }
      getLog().info(logLine);
      archiver.createArchive(getSession(), getProject(), archive);
      return jarFile;

    } catch (Exception e) {
      throw new IllegalStateException("Fail to build SonarQube plugin", e);
    }
  }

  private File createZipArchive(File jarFile) {
    File zipFile = getZipFile(getOutputDirectory(), getFinalName(), getClassifier());
    zipArchiver.setDestFile(zipFile);
    try {
      zipArchiver.addFile(jarFile, ZIP_PLUGIN_ENTRY_PREFIX + jarFile.getName());
      if (resourcesDirectory.isDirectory()) {
        zipArchiver.addDirectory(resourcesDirectory, ZIP_RESOURCES_ENTRY_PREFIX, getIncludes(), getExcludes());
      } else {
        getLog().debug(format("No resources directory found at '%s'; zip will have no 'resources/' entries", resourcesDirectory));
      }
      zipArchiver.createArchive();
      return zipFile;
    } catch (Exception e) {
      throw new IllegalStateException("Fail to build SonarQube plugin zip", e);
    }
  }

  /**
   * Compute build date.
   *
   * Take value of {@link #REPRODUCIBLE_BUILDS_PROPERTY} property if set, otherwise take current datetime.
   *
   * @return Date of the build.
   */
  private Instant getBuildDate() {
    final String outputTimestamp = getProject().getProperties().getProperty(REPRODUCIBLE_BUILDS_PROPERTY);
    final Log log = getLog();
    if (outputTimestamp == null) {
      if (log.isDebugEnabled()) {
        log.debug(format("property '%s' is not set. Will take current date for the build date.", REPRODUCIBLE_BUILDS_PROPERTY));
      }
      return Instant.now();
    }
    try {
      return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(outputTimestamp));
    } catch (DateTimeParseException e) {
      log.warn(format("Unable to parse value of property '%s': %s.  Will take current date for the build date.", REPRODUCIBLE_BUILDS_PROPERTY, outputTimestamp), e);
      return Instant.now();
    }
  }

  private void addManifestProperty(PluginManifestProperty property, @Nullable Object value) {
    getLog().info(format("    %s: %s", property.getLabel(), firstNonNull(value, "")));
    if (value != null) {
      archive.addManifestEntry(property.getKey(), value.toString());
    }
  }

  @CheckForNull
  private String getDevelopers() {
    if (getProject().getDevelopers() != null) {
      return Joiner.on(",").join(
        Iterables.transform(getProject().getDevelopers(), developer -> checkNotNull(developer.getName(), "Developer name must not be null")));
    }
    return null;
  }

  private List<String> copyDependencies() throws IOException, DependencyTreeBuilderException {
    List<String> libs = new ArrayList<>();
    File libDirectory = new File(getAppDirectory(), LIB_DIR);
    Set<Artifact> artifacts = getNotProvidedDependencies();
    for (Artifact artifact : artifacts) {
      String targetFileName = getDefaultFinalName(artifact);
      FileUtils.copyFileIfModified(artifact.getFile(), new File(libDirectory, targetFileName));
      libs.add(LIB_DIR + targetFileName);
    }
    return libs;
  }

  private static String getDefaultFinalName(Artifact artifact) {
    return artifact.getFile().getName();
  }

  private Set<Artifact> getNotProvidedDependencies() throws DependencyTreeBuilderException {
    Set<Artifact> result = new HashSet<>();
    Set<Artifact> providedArtifacts = getSonarProvidedArtifacts();
    for (Artifact artifact : getIncludedArtifacts()) {
      boolean include = true;
      if (isSonarPlugin(artifact) || isScopeProvidedOrTest(artifact)) {
        include = false;
      }
      if (containsArtifact(providedArtifacts, artifact)) {
        getLog().warn(artifact + " is provided by SonarQube plugin API and will not be packaged in your plugin");
        include = false;
      }
      if (include) {
        result.add(artifact);
      }
    }
    return result;
  }

  private static boolean isScopeProvidedOrTest(Artifact artifact) {
    return Artifact.SCOPE_PROVIDED.equals(artifact.getScope()) || Artifact.SCOPE_TEST.equals(artifact.getScope());
  }

  private static boolean isSonarPlugin(Artifact artifact) {
    return "sonar-plugin".equals(artifact.getType());
  }

  private static boolean containsArtifact(Set<Artifact> artifacts, Artifact artifact) {
    for (Artifact a : artifacts) {
      if (StringUtils.equals(a.getGroupId(), artifact.getGroupId()) &&
        StringUtils.equals(a.getArtifactId(), artifact.getArtifactId())) {
        return true;
      }
    }
    return false;
  }

  private Set<Artifact> getSonarProvidedArtifacts() throws DependencyTreeBuilderException {
    Set<Artifact> result = new HashSet<>();
    ArtifactFilter artifactFilter = new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME);
    // We need to use Maven 2 dependency tree in order to get omitted dependencies
    DependencyNode rootNode = dependencyTreeBuilder.buildDependencyTree(getProject(),
      localRepository,
      artifactFilter);
    searchForSonarProvidedArtifacts(rootNode, result, false);
    return result;
  }

  private static void searchForSonarProvidedArtifacts(@Nullable DependencyNode dependency, Set<Artifact> sonarArtifacts, boolean isParentProvided) {
    if (dependency != null) {
      boolean provided;
      if (dependency.getParent() != null) {
        // - Skip check on root node - see SONAR-1815
        provided = isParentProvided ||
          ("org.codehaus.sonar".equals(dependency.getArtifact().getGroupId()) && !Artifact.SCOPE_TEST.equals(dependency.getArtifact().getScope()));
      } else {
        provided = isParentProvided;
      }

      if (provided) {
        sonarArtifacts.add(dependency.getArtifact());
      }

      if (!Artifact.SCOPE_TEST.equals(dependency.getArtifact().getScope())) {
        for (DependencyNode childDep : dependency.getChildren()) {
          searchForSonarProvidedArtifacts(childDep, sonarArtifacts, provided);
        }
      }
    }
  }

  private String[] getIncludes() {
    if (includes != null && includes.length > 0) {
      return includes;
    }
    return DEFAULT_INCLUDES;
  }

  private String[] getExcludes() {
    if (excludes != null && excludes.length > 0) {
      return excludes;
    }
    return DEFAULT_EXCLUDES;
  }

  private String[] getJarExcludes() {
    String[] base = getExcludes();
    if (!zipPackaging) {
      return base;
    }
    String exclude = getResourcesDirectoryExcludePattern();
    if (exclude == null) {
      return base;
    }
    getLog().warn(format("Resources directory '%s' is excluded from the plugin JAR; its content will only be "
      + "available in the 'resources/' folder of the ZIP artifact.", resourcesDirectory));
    String[] combined = Arrays.copyOf(base, base.length + 1);
    combined[base.length] = exclude;
    return combined;
  }

  @CheckForNull
  private String getResourcesDirectoryExcludePattern() {
    Path standardResourcesPath = new File(getProject().getBasedir(), STANDARD_RESOURCES_DIR).toPath().normalize();
    Path resourcesPath = resourcesDirectory.toPath().normalize();
    if (!resourcesPath.startsWith(standardResourcesPath) || resourcesPath.equals(standardResourcesPath)) {
      return null;
    }
    return standardResourcesPath.relativize(resourcesPath).toString().replace(File.separatorChar, '/') + "/**";
  }

  protected static File getJarFile(File basedir, String finalName, String classifier) {
    return new File(basedir, finalName + getClassifierSuffix(classifier) + ".jar");
  }

  protected static File getZipFile(File basedir, String finalName, String classifier) {
    return new File(basedir, finalName + getClassifierSuffix(classifier) + ".zip");
  }

  private static String getClassifierSuffix(String classifier) {
    if (StringUtils.isBlank(classifier)) {
      return "";
    } else if (classifier.charAt(0) == '-') {
      return classifier;
    }
    return "-" + classifier;
  }

}
