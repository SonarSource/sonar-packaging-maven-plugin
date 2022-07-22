package org.sonar.plugins.sample;

import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;

public class SampleSensor implements Sensor {
  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.name("Compute size of file names");
  }

  @Override
  public void execute(SensorContext context) {
    FileSystem fs = context.fileSystem();
    // only "main" files, but not "tests"
    Iterable<InputFile> files = fs.inputFiles(fs.predicates().hasType(InputFile.Type.MAIN));
    for (InputFile file : files) {
      context.<Integer>newMeasure()
        .forMetric(SampleMetrics.FILENAME_SIZE)
        .on(file)
        .withValue(file.filename().length())
        .save();
    }
  }
}
