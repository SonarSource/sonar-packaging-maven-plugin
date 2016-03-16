#!/bin/bash

set -euo pipefail

function configureTravis {
  mkdir ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v27 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}
configureTravis

set -euo pipefail

echo '======= Build, deploy and analyze master'

  # Fetch all commit history so that SonarQube has exact blame information
  # for issue auto-assignment
  # This command can fail with "fatal: --unshallow on a complete repository does not make sense"
  # if there are not enough commits in the Git repository (even if Travis executed git clone --depth 50).
  # For this reason errors are ignored with "|| true"
  #git fetch --unshallow || true

  # Analyze with SNAPSHOT version as long as SQ does not correctly handle
  # purge of release data
  SONAR_PROJECT_VERSION=`maven_expression "project.version"`

  # Do not deploy a SNAPSHOT version but the release version related to this build
  set_maven_build_version $TRAVIS_BUILD_NUMBER

  export MAVEN_OPTS="-Xmx1536m -Xms128m"
  mvn org.jacoco:jacoco-maven-plugin:prepare-agent install sonar:sonar \
      -Pcoverage-per-test \
      -Dmaven.test.redirectTestOutputToFile=false \
      -Dsonar.host.url=$SONAR_HOST_URL \
      -Dsonar.login=$SONAR_TOKEN \
      -Dsonar.projectVersion=$SONAR_PROJECT_VERSION \
      -B -e -V -X
