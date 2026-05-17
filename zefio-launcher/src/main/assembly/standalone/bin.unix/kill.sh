#!/bin/sh

# 💡 1. Use the POSIX standard '.' operator instead of 'source' for AIX/Unix compatibility
BIN_DIR="$(cd "$(dirname "$0")" && pwd)"
. "$BIN_DIR/env.sh"

if ( checkRunning );then
  PID=$(getAppPID)
  echo "Zefio stop (pid '$PID')"
  kill -15 $PID
  else
    echo "Zefio already stop "
fi
