#!/bin/bash

set -euo pipefail

function configureTravis {
  mkdir -p ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v61 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
  source ~/.local/bin/setup_promote_environment
}
configureTravis

export DEPLOY_PULL_REQUEST=true

. regular_mvn_build_deploy_analyze -Psign
promote
