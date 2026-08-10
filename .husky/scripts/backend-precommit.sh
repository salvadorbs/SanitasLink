#!/usr/bin/env bash
set -e

STAGED_BACKEND=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^backend/' || true)
if [ -z "$STAGED_BACKEND" ]; then
  exit 0
fi

if [ -z "$JAVA_HOME" ] && [ -x /usr/lib/jvm/java-21-openjdk/bin/java ]; then
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
fi
export PATH="$JAVA_HOME/bin:$PATH"

echo "Backend pre-commit: compiling + spotless (formatting/quality)"
(cd backend && ./mvnw -q -DskipTests spotless:check compile)

echo "Backend pre-commit: scanning staged files for secrets"
if grep -lE 'BEGIN (RSA|OPENSSH|EC|DSA) PRIVATE KEY|AKIA[0-9A-Z]{16}|ghp_[0-9A-Za-z]{36}|gho_[0-9A-Za-z]{36}|sk_live_[0-9a-zA-Z]{20,}|AIza[0-9A-Za-z_-]{35}' $STAGED_BACKEND >/dev/null 2>&1; then
  echo "!! Possible secret detected in staged backend files. Aborting." >&2
  exit 1
fi
