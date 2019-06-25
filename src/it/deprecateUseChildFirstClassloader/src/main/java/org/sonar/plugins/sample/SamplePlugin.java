package org.sonar.plugins.sample;

import java.util.Arrays;
import java.util.List;
import org.sonar.api.SonarPlugin;

/**
 * This class is the container for all others extensions
 */
public class SamplePlugin extends SonarPlugin {

  public List getExtensions() {
    return Arrays.asList(SampleMetrics.class, SampleSensor.class);
  }
}
