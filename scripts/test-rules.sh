#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ -d ".tools/jdk-17.0.21.1+2/Contents/Home" ]; then
  JAVA_HOME="$PWD/.tools/jdk-17.0.21.1+2/Contents/Home"
  export JAVA_HOME
fi
mkdir -p .tools/test-classes
"$JAVA_HOME/bin/javac" -encoding UTF-8 -d .tools/test-classes app/src/main/java/cn/ryan/bookkeeper/LedgerRules.java tests/LedgerRulesTest.java
"$JAVA_HOME/bin/java" -cp .tools/test-classes LedgerRulesTest
