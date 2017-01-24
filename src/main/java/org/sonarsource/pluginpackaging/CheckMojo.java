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

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static java.lang.String.format;

@Mojo(name = "check", defaultPhase = LifecyclePhase.INITIALIZE, requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public class CheckMojo extends AbstractSonarMojo {

  private static final String[] UNSUPPORTED_GROUP_IDS = {"org.apache.logging.log4j", "log4j", "commons-logging"};

  @Override
  public void execute() throws MojoExecutionException {
    checkPluginName();
    checkPluginKey();
    checkRequiredApi();
    checkUnsupportedDependencies();
  }

  private void checkPluginKey() throws MojoExecutionException {
    String key = getPluginKey();
    if (StringUtils.isBlank(key) || !PluginKeyUtils.isValid(key)) {
      throw new MojoExecutionException("Plugin key is badly formatted. Please use ascii letters and digits only: " + key);
    }
  }

  private void checkPluginName() throws MojoExecutionException {
    // Maven 2 automatically sets the name as "Unnamed - <artifactId>" when the field <name> is missing.
    // Note that Maven 3 has a different behavior. Name is the artifact id by default.
    if (getPluginName().startsWith("Unnamed - ")) {
      throw new MojoExecutionException("Plugin name is missing. "
        + "Please add the field <name> or the property sonar.pluginName.");
    }
  }

  private void checkRequiredApi() throws MojoExecutionException {
    Artifact api = getNullablePluginApiArtifact();
    if (api == null || !Artifact.SCOPE_PROVIDED.equals(api.getScope())) {
      throw new MojoExecutionException(
        SONAR_PLUGIN_API_ARTIFACTID + " must be declared in dependencies with scope <provided>");
    }
  }

  private void checkUnsupportedDependencies() throws MojoExecutionException {
    boolean ok = true;
    for (Artifact artifact : getIncludedArtifacts()) {
      if (ArrayUtils.contains(UNSUPPORTED_GROUP_IDS, artifact.getGroupId())) {
        logMustBeProvided(artifact);
        ok = false;
      }
    }
    if (!ok) {
      throw new MojoExecutionException("Unsupported dependencies");
    }
  }

  private void logMustBeProvided(Artifact artifact) {
    getLog().error(format("This dependency must be declared with scope <provided>: %s", artifact.getDependencyConflictId()));
  }
}
