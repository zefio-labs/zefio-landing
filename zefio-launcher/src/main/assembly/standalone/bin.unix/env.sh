#!/bin/sh

##############################
# Base Directory             #
##############################
BIN_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$BIN_DIR")"

##############################
# Log Directory Customization#
##############################
export APP_NAME="Zefio"
LOG_DIR_VALUE="${BASE_DIR}/logs"

##############################
# OS Detection               #
##############################
OS_TYPE=$(uname -s)
echo "Detected OS Type: $OS_TYPE"

##############################
# Java Home & Executable     #
##############################
export JAVA_HOME=${BASE_DIR}/@jdk.folder@
export JAVA_EXEC="$JAVA_HOME/bin/java"
export PATH=$JAVA_HOME/bin:$PATH

##############################
# Classpath #
##############################
CLASSPATH=".:$BASE_DIR/resources:$BASE_DIR/lib/*"
export CLASSPATH

##############################
# Java Version Detection     #
##############################
JAVA_FULL_VERSION=$($JAVA_EXEC -version 2>&1 | awk -F\" '/version/ {print $2}')
case "$JAVA_FULL_VERSION" in
  1.*)
    JAVA_VERSION=$(echo "$JAVA_FULL_VERSION" | cut -d. -f2)
    ;;
  *)
    JAVA_VERSION=$(echo "$JAVA_FULL_VERSION" | cut -d. -f1)
    ;;
esac

if [ "$JAVA_VERSION" -ge 21 ]; then
  JVM_VERSION="jdk21"
else
  JVM_VERSION="jdk8"
fi

echo "Detected Java Version: $JAVA_VERSION ($JVM_VERSION)"

################################
# Environment Type: dev or prd #
################################
ENV_TYPE=${APP_ENV:-prd}  # Default to 'prd' if not set

echo "Running in '$ENV_TYPE' mode"

############################
# Function Options         #
############################
checkRunning() {
  PID=$(getAppPID)
  if [ -z "$PID" ]; then
    return 1  # Not running
  else
    return 0  # Already running
  fi
}
getAppPID() {
  if [ "$OS_TYPE" = "AIX" ]; then
    # AIX-specific extraction method (uses auxww to prevent truncated ps arguments)
    ps auxww | awk -v tgt="-DNAME=${APP_NAME}" '{ for(i=1;i<=NF;i++) if($i == tgt) print $2 }'
  else
    # Linux / Darwin environment (uses default ps -ef)
    ps -ef | awk -v tgt="-DNAME=${APP_NAME}" '{ for(i=1;i<=NF;i++) if($i == tgt) print $2 }'
  fi
}

###########################
# JVM Options              #
###########################

# 1. Common JVM options (GC algorithm, encoding, process name identification)
COMMON_OPTS="-XX:+UseG1GC -DNAME=${APP_NAME} -Djava.net.preferIPv4Stack=true -Dfile.encoding=utf-8"

# 2. Memory options
if [ "$ENV_TYPE" = "prd" ]; then
  # Production: Fix Min/Max equally to prevent OOM and minimize GC overhead
  MEM_OPTS="-Xms256m -Xmx512m -XX:MaxDirectMemorySize=512m \
              -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$LOG_DIR_VALUE/heapdump.hprof \
              -XX:+ExitOnOutOfMemoryError"
else
  # Dev/Test: Set base sizes while allowing flexibility
  MEM_OPTS="-Xms256m -Xmx512m -XX:MaxDirectMemorySize=512m \
              -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$LOG_DIR_VALUE/heapdump.hprof"
fi

# 3. JVM and Netty monitoring/optimization options by version
if [ "$JVM_VERSION" = "jdk21" ]; then
  # GC log format for JDK 9 and above
  GC_LOG_OPTS="-Xlog:gc*:file=$LOG_DIR_VALUE/gc.log:time,uptime,level,tags:filecount=5,filesize=10M"

  # JDK 16+ module encapsulation defense and Netty direct memory access optimization
  NETTY_OPTS="--add-opens java.base/java.nio=ALL-UNNAMED \
                --add-opens java.base/sun.misc=ALL-UNNAMED \
                --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
                -Dio.netty.tryReflectionSetAccessible=true \
                -Djdk.nio.maxCachedBufferSize=262144"
else
  # JDK 1.8 compatible GC log format
  GC_LOG_OPTS="-verbose:gc -Xloggc:$LOG_DIR_VALUE/gc.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=10M"
  # No additional options needed for JDK 1.8 (prevents errors)
  NETTY_OPTS=""
fi

# Additional options based on environment
if [ "$ENV_TYPE" = "prd" ]; then
  # Production: Keep leak detection at SIMPLE level for performance
  ENV_OPTS="-Dspring.profiles.active=$ENV_TYPE -Dio.netty.leakDetection.level=SIMPLE"
else
  # Dev: Create dump on OOM and track Netty memory leaks at PARANOID (100%) level
  ENV_OPTS="-Dspring.profiles.active=$ENV_TYPE -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$LOG_DIR_VALUE/heapdump.hprof -Dio.netty.leakDetection.level=PARANOID"
fi

# Add log directory property
ENV_OPTS="$ENV_OPTS -DLOG_DIR=$LOG_DIR_VALUE"

# 5. Final JAVA_OPTS merge
export JAVA_OPTS="$MEM_OPTS $COMMON_OPTS $GC_LOG_OPTS $NETTY_OPTS $ENV_OPTS"
