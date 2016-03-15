package org.sonar.plugins.sample;

import org.sonar.api.SonarPlugin;

import java.util.Collections;
import java.util.List;

public class SamplePlugin extends SonarPlugin {
  public List getExtensions() {
    return Collections.emptyList();
  }
}
