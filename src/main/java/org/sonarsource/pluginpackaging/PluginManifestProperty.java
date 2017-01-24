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

public enum PluginManifestProperty {

  KEY("Plugin-Key", "Key"),
  NAME("Plugin-Name", "Name"),
  DESCRIPTION("Plugin-Description", "Description"),
  MAIN_CLASS("Plugin-Class", "Entry-point Class"),
  ORGANIZATION("Plugin-Organization", "Organization"),
  ORGANIZATION_URL("Plugin-OrganizationUrl", "Organization URL"),
  LICENSE("Plugin-License", "Licensing"),
  VERSION("Plugin-Version", "Version"),
  DISPLAY_VERSION("Plugin-Display-Version", "Display Version"),
  SONAR_VERSION("Sonar-Version", "Minimal SonarQube Version"),
  DEPENDENCIES("Plugin-Dependencies", "Dependencies"),
  HOMEPAGE("Plugin-Homepage", "Homepage URL"),
  TERMS_CONDITIONS_URL("Plugin-TermsConditionsUrl", "Terms and Conditions"),
  BUILD_DATE("Plugin-BuildDate", "Build date"),
  ISSUE_TRACKER_URL("Plugin-IssueTrackerUrl", "Issue Tracker URL"),
  REQUIRE_PLUGINS("Plugin-RequirePlugins", "Required Plugins"),
  SONARLINT_SUPPORTED("SonarLint-Supported", "Does the plugin support SonarLint?"),
  USE_CHILD_FIRST_CLASSLOADER("Plugin-ChildFirstClassLoader", "Use Child-first ClassLoader"),
  BASE_PLUGIN("Plugin-Base", "Base Plugin"),
  SOURCES_URL("Plugin-SourcesUrl", "Sources URL"),
  DEVELOPERS("Plugin-Developers", "Developers");

  private final String key;
  private final String label;

  PluginManifestProperty(String key, String label) {
    this.key = key;
    this.label = label;
  }

  public String getKey() {
    return key;
  }

  public String getLabel() {
    return label;
  }
}
