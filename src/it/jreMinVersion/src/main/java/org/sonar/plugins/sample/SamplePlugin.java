package org.sonar.plugins.sample;

import java.util.Collections;
import java.util.List;
import org.sonar.api.SonarPlugin;

public class SamplePlugin extends SonarPlugin {
  public List getExtensions() {
    return Collections.emptyList();
  }
}
