#!/bin/sh

# 💡 1. Use the POSIX standard '.' operator instead of 'source' for AIX/Unix compatibility
BIN_DIR="$(cd "$(dirname "$0")" && pwd)"
. "$BIN_DIR/env.sh"

LINK_ENV="@java.env@"
if [ -n "$LINK_ENV" ]; then
  export $(echo $LINK_ENV | tr ';' '\n')
fi

JAVA_OPTS="${JAVA_OPTS} -Dspring.config.additional-location=file:${BASE_DIR}/resources/"
JAVA_OPTS="${JAVA_OPTS} @java.opts@"

echo "JAVA_OPTS set to:"
echo "$JAVA_OPTS"

if ( checkRunning );then
  PID=$(getAppPID)
  echo "Zefio is already running. ( '$PID' )"
  else
    echo 'Zefio app in Background.'
  nohup ${JAVA_EXEC} $JAVA_OPTS io.zefio.launcher.ZefioApplication > /dev/null 2>&1 &
fi
