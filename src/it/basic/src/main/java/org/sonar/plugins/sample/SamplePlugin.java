package org.sonar.plugins.sample;

import org.sonar.api.Plugin;

/**
 * This class is the entry point for all extensions. It is referenced in pom.xml.
 */
public class SamplePlugin implements Plugin {

  @Override
  public void define(Context context) {
    context.addExtensions(SampleSensor.class, SampleMetrics.class);

  }
}
