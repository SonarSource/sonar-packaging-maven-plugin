#!/bin/bash

set -euo pipefail

function installTravisTools {
  mkdir ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v19 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}

installTravisTools

case "$TESTS" in

CI)
  mvn verify -B -e -V -Prun-its
  ;;

esac

