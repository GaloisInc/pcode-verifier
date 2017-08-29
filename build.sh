#!/bin/bash
#
# This script builds `crucible-server` and PCode Java Libraries.
#
# Prerequisites:
#
#   * stack
#      - https://github.com/commercialhaskell/stack/wiki/Downloads
#
#   * GHC >= 7.10 (Optional)
#      - https://www.haskell.org/ghc/download
#
# Installation:
#
# Execute this script from the `pcode-verifier` source repo. This will checkout
# all needed Galois dependencies in `$PWD/..` and execute a build.

set -e

# Update submodules
git submodule init
git submodule update

echo "** Installing GHC if needed. **"
stack setup

echo "** Starting crucible-server build **"
stack install crucible-server

export STACK_INSTALL=`stack path --local-install-root`
export CRUCIBLE_SERVER="${STACK_INSTALL}/bin/crucible-server"

echo "Crucible server location:"
echo $CRUCIBLE_SERVER

echo "** Building PCode parser **"
(cd JavaParser; mvn install)

echo "** Building Crucible Java API **"
(cd dependencies/crucible/crucible-server/java_api; mvn -DcrucibleHome="${STACK_INSTALL}" install)

echo "** Building PCode<->Crucible bridge **"
(cd PCodeCrucible; mvn -DcrucibleHome="${STACK_INSTALL}" install)

echo "DONE."
