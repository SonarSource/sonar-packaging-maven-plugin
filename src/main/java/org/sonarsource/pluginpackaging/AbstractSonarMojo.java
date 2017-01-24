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

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.License;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

public abstract class AbstractSonarMojo extends AbstractMojo {

  public static final String SONAR_PLUGIN_API_ARTIFACTID = "sonar-plugin-api";
  public static final String SONAR_PLUGIN_API_TYPE = "jar";

  /**
   * The Maven project.
   */
  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  /**
   *  Maven Session
   */
  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  /**
   * Directory containing the generated JAR.
   */
  @Parameter(property = "project.build.directory", required = true)
  private File outputDirectory;

  /**
   * Directory containing the classes and resource files that should be packaged into the JAR.
   */
  @Parameter(property = "project.build.outputDirectory", required = true)
  private File classesDirectory;

  /**
  * The directory where the app is built.
  */
  @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}", required = true)
  private File appDirectory;

  /**
   * Name of the generated JAR.
   */
  @Parameter(alias = "jarName", property = "project.build.finalName", required = true)
  private String finalName;

  /**
   * Classifier to add to the artifact generated. If given, the artifact will be an attachment instead.
   */
  @Parameter
  private String classifier;

  @Component
  protected MavenProjectHelper projectHelper;

  @Parameter(property = "sonar.pluginKey", defaultValue = "${project.artifactId}", required = true)
  protected String pluginKey;

  @Parameter(property = "sonar.pluginTermsConditionsUrl")
  private URL pluginTermsConditionsUrl;

  @Parameter(property = "sonar.pluginDisplayVersion")
  private String pluginDisplayVersion;

  @Parameter(property = "sonar.pluginClass", required = true)
  private String pluginClass;

  @Parameter(property = "sonar.sonarLintSupported", defaultValue = "false")
  private boolean sonarLintSupported;

  @Parameter(property = "sonar.pluginName", defaultValue = "${project.name}", required = true)
  private String pluginName;

  @Parameter()
  private String sonarQubeMinVersion;

  @Parameter(property = "sonar.requirePlugins")
  protected String requirePlugins;

  @Parameter(property = "sonar.pluginDescription", defaultValue = "${project.description}", required = true)
  private String pluginDescription;

  @Parameter(property = "sonar.pluginSourcesUrl", defaultValue = "${project.scm.url}")
  private URL pluginSourcesUrl;

  @Parameter(property = "sonar.pluginOrganizationName", defaultValue = "${project.organization.name}")
  private String pluginOrganizationName;

  @Parameter(property = "sonar.pluginOrganizationUrl", defaultValue = "${project.organization.url}")
  private URL pluginOrganizationUrl;

  @Parameter(property = "sonar.pluginUrl", defaultValue = "${project.url}")
  private URL pluginUrl;

  @Parameter(defaultValue = "${project.issueManagement.url}")
  private URL pluginIssueTrackerUrl;

  @Parameter(property = "sonar.useChildFirstClassLoader", defaultValue = "false")
  private boolean useChildFirstClassLoader;

  @Parameter(property = "sonar.basePlugin")
  private String basePlugin;

  @Parameter(property = "sonar.skipDependenciesPackaging", defaultValue = "false")
  private boolean skipDependenciesPackaging;

  @Parameter(property = "sonar.addMavenDescriptor", defaultValue = "true")
  private boolean addMavenDescriptor;

  protected final MavenProject getProject() {
    return project;
  }

  protected final MavenSession getSession() {
    return session;
  }

  protected final File getOutputDirectory() {
    return outputDirectory;
  }

  /**
   * @return the main classes directory, so it's used as the root of the jar.
   */
  protected final File getClassesDirectory() {
    return classesDirectory;
  }

  public File getAppDirectory() {
    return appDirectory;
  }

  protected final String getFinalName() {
    return finalName;
  }

  protected final String getClassifier() {
    return classifier;
  }

  protected final String getPluginKey() {
    return PluginKeyUtils.sanitize(pluginKey);
  }

  protected final String getPluginDisplayVersion() {
    return pluginDisplayVersion == null ? getProject().getVersion() : pluginDisplayVersion;
  }

  protected final String getPluginClass() {
    return pluginClass;
  }

  protected final boolean isSonarLintSupported() {
    return sonarLintSupported;
  }

  protected final String getPluginName() {
    return pluginName;
  }

  @CheckForNull
  protected final String getSonarQubeMinVersion() {
    return sonarQubeMinVersion;
  }

  @CheckForNull
  protected final String getPluginOrganizationName() {
    return pluginOrganizationName;
  }

  @CheckForNull
  protected final URL getPluginOrganizationUrl() {
    return pluginOrganizationUrl;
  }

  @CheckForNull
  protected final URL getPluginSourcesUrl() {
    return pluginSourcesUrl;
  }

  @CheckForNull
  protected final String getRequiredPlugins() {
    return requirePlugins;
  }

  protected final String getLicensing() {
    List<String> licenses = new ArrayList<>();
    if (getProject().getLicenses() != null) {
      for (Object license : getProject().getLicenses()) {
        License l = (License) license;
        if (l.getName() != null) {
          licenses.add(l.getName());
        }
      }
    }
    return StringUtils.join(licenses, " ");
  }

  @CheckForNull
  protected final String getPluginDescription() {
    return pluginDescription;
  }

  @CheckForNull
  protected final URL getPluginUrl() {
    return pluginUrl;
  }

  @CheckForNull
  protected final URL getPluginTermsConditionsUrl() {
    return pluginTermsConditionsUrl;
  }

  @CheckForNull
  protected final URL getPluginIssueTrackerUrl() {
    return pluginIssueTrackerUrl;
  }

  protected final boolean isUseChildFirstClassLoader() {
    return useChildFirstClassLoader;
  }

  @CheckForNull
  protected final String getBasePlugin() {
    return basePlugin;
  }

  protected final boolean isSkipDependenciesPackaging() {
    return skipDependenciesPackaging;
  }

  protected final boolean isAddMavenDescriptor() {
    return addMavenDescriptor;
  }

  @SuppressWarnings({"unchecked"})
  protected final Set<Artifact> getDependencyArtifacts() {
    return getProject().getDependencyArtifacts();
  }

  @SuppressWarnings({"unchecked"})
  protected final Set<Artifact> getIncludedArtifacts() {
    Set<Artifact> result = new HashSet<>();
    Set<Artifact> artifacts = getProject().getArtifacts();
    ScopeArtifactFilter filter = new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME);
    for (Artifact artifact : artifacts) {
      if (filter.include(artifact)) {
        result.add(artifact);
      }
    }
    return result;
  }

  @CheckForNull
  protected final Artifact getNullablePluginApiArtifact() {
    Set<Artifact> dependencies = getDependencyArtifacts();
    if (dependencies != null) {
      for (Artifact dep : dependencies) {
        if (SONAR_PLUGIN_API_ARTIFACTID.equals(dep.getArtifactId())
          && SONAR_PLUGIN_API_TYPE.equals(dep.getType())) {
          return dep;
        }
      }
    }
    return null;
  }

  protected final Artifact getPluginApiArtifact() {
    Artifact result = getNullablePluginApiArtifact();
    if (result == null) {
      throw new IllegalStateException("Plugin API is not declared");
    }
    return result;
  }
}
