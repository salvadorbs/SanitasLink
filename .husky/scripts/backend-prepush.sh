#!/usr/bin/env bash
set -e

if [ -z "$JAVA_HOME" ] && [ -x /usr/lib/jvm/java-21-openjdk/bin/java ]; then
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
fi
export PATH="$JAVA_HOME/bin:$PATH"

echo "Backend pre-push: running full verify (Flyway + RLS + JWT tests with Testcontainers)"
(cd backend && ./mvnw -q clean verify)
