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

# GitHub repos (some private, some public) required by the build
REPO_LIST="abcBridge aig hpb parameterized-utils mss saw-core"

# base GitHub URL for Galois repos
GITHUB_URL="git@github.com:GaloisInc"

# where we clone external dependencies
EXT='..'

# Download GitHub repos
for repo in $REPO_LIST
do
  if [ ! -d "$EXT/$repo" ]; then
      pushd $EXT
      git clone ${GITHUB_URL}/${repo}.git
      if [ $? -ne 0 ]; then
          echo "\n\nFailed to clone private GitHub repos. Please check your \
                ssh keys to make sure you are authorized for the Galois GitHub \
                account"
          exit 1
      fi
      popd
  else
      pushd "$EXT/$repo"
      git pull
      popd
  fi
done


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
(cd $EXT/mss/crucible-server/java_api; mvn -DcrucibleHome="${STACK_INSTALL}" install)

echo "** Building PCode<->Crucible bridge **"
(cd PCodeCrucible; mvn -DcrucibleHome="${STACK_INSTALL}" install)

echo "DONE."
