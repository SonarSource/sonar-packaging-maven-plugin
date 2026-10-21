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
import java.util.HashSet;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
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
   * Directory containing the generated JAR.
   */
  @Parameter(property = "project.build.directory", required = true)
  private File outputDirectory;

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

  @Parameter(property = "sonar.pluginName", defaultValue = "${project.name}", required = true)
  private String pluginName;

  protected final MavenProject getProject() {
    return project;
  }

  protected final File getOutputDirectory() {
    return outputDirectory;
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

  protected final String getPluginName() {
    return pluginName;
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

  protected static File getJarFile(File basedir, String finalName, String classifier) {
    return new File(basedir, finalName + getClassifierSuffix(classifier) + ".jar");
  }

  protected static String getClassifierSuffix(String classifier) {
    if (StringUtils.isBlank(classifier)) {
      return "";
    } else if (classifier.charAt(0) == '-') {
      return classifier;
    }
    return "-" + classifier;
  }
}
