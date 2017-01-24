/*
 * SonarQube :: Packaging Maven Plugin
 * Copyright (C) 2009-2017 SonarSource SA
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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.model.Developer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilderException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.util.FileUtils;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * Build a SonarQube Plugin from the current project.
 */
@Mojo(name = "sonar-plugin", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public class SonarPluginMojo extends AbstractSonarMojo {

  private static final String DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ssZ";
  private static final String LIB_DIR = "META-INF/lib/";
  private static final String[] DEFAULT_EXCLUDES = new String[] {"**/package.html"};
  private static final String[] DEFAULT_INCLUDES = new String[] {"**/**"};

  @Component(role = org.codehaus.plexus.archiver.Archiver.class, hint = "jar")
  protected JarArchiver jarArchiver;

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
   * The archive configuration to use.
   * See <a href="http://maven.apache.org/shared/maven-archiver/index.html">Maven Archiver Reference</a>.
   */
  @Parameter
  private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

  @Component
  private DependencyTreeBuilder dependencyTreeBuilder;

  @Parameter(defaultValue = "${localRepository}", readonly = true)
  private ArtifactRepository localRepository;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    checkPluginClass();
    File jarFile = createArchive();
    String classifier = getClassifier();
    if (classifier != null) {
      projectHelper.attachArtifact(getProject(), "jar", classifier, jarFile);
    } else {
      getProject().getArtifact().setFile(jarFile);
    }
  }

  private void checkPluginClass() throws MojoExecutionException {
    String classname = getPluginClass();
    if (!new File(getClassesDirectory(), classname.replace('.', '/') + ".class").exists()) {
      throw new MojoExecutionException("Plugin class not found: '" + classname);
    }
  }

  public File createArchive() throws MojoExecutionException {
    File jarFile = getJarFile(getOutputDirectory(), getFinalName(), getClassifier());
    MavenArchiver archiver = new MavenArchiver();
    archiver.setArchiver(jarArchiver);
    archiver.setOutputFile(jarFile);

    try {
      archiver.getArchiver().addDirectory(getClassesDirectory(), getIncludes(), getExcludes());
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
      addManifestProperty(PluginManifestProperty.USE_CHILD_FIRST_CLASSLOADER, String.valueOf(isUseChildFirstClassLoader()));
      addManifestProperty(PluginManifestProperty.BASE_PLUGIN, getBasePlugin());
      addManifestProperty(PluginManifestProperty.HOMEPAGE, getPluginUrl());
      addManifestProperty(PluginManifestProperty.SONAR_VERSION, firstNonNull(getSonarQubeMinVersion(), getPluginApiArtifact().getVersion()));
      addManifestProperty(PluginManifestProperty.LICENSE, getLicensing());
      addManifestProperty(PluginManifestProperty.ORGANIZATION, getPluginOrganizationName());
      addManifestProperty(PluginManifestProperty.ORGANIZATION_URL, getPluginOrganizationUrl());
      addManifestProperty(PluginManifestProperty.TERMS_CONDITIONS_URL, getPluginTermsConditionsUrl());
      addManifestProperty(PluginManifestProperty.ISSUE_TRACKER_URL, getPluginIssueTrackerUrl());
      addManifestProperty(PluginManifestProperty.BUILD_DATE, new SimpleDateFormat(DATETIME_PATTERN).format(new Date()));
      addManifestProperty(PluginManifestProperty.SOURCES_URL, getPluginSourcesUrl());
      addManifestProperty(PluginManifestProperty.DEVELOPERS, getDevelopers());
      if (isSkipDependenciesPackaging()) {
        getLog().info("Skip packaging of dependencies");

      } else {
        List<String> libs = copyDependencies();
        if (!libs.isEmpty()) {
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
        Iterables.transform(getProject().getDevelopers(), new Function<Developer, String>() {
          @Override
          public String apply(Developer developer) {
            return checkNotNull(developer.getName(), "Developer name must not be null");
          }
        }));
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
        for (Object childDep : dependency.getChildren()) {
          searchForSonarProvidedArtifacts((DependencyNode) childDep, sonarArtifacts, provided);
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

  protected static File getJarFile(File basedir, String finalName, String classifier) {
    String suffix;
    if (StringUtils.isBlank(classifier)) {
      suffix = "";
    } else if (classifier.charAt(0) == '-') {
      suffix = classifier;
    } else {
      suffix = "-" + classifier;
    }
    return new File(basedir, finalName + suffix + ".jar");
  }

}
