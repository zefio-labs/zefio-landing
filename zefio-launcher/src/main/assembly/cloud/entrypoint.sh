#!/bin/bash

# 1. Base 경로 및 변수 설정
export BASE_DIR="/Zefio"
export LOG_DIR="${BASE_DIR}/logs"
export APP_NAME="Zefio"
export CLASSPATH=".:$BASE_DIR/resources:$BASE_DIR/lib/*"

ENV_TYPE=${APP_ENV:-prd}

# 2. 공통 기본 옵션 (✅ application.version1.0 로 수정됨!)
COMMON_OPTS="-XX:+UseG1GC -DNAME=${APP_NAME} -Dapplication.version=1.0 -Dspring.config.location=classpath:/extreme-composite.yaml -Djava.net.preferIPv4Stack=true -Dfile.encoding=utf-8"
LOG_OPTS="-DLOG_DIR=${LOG_DIR}"

# ==========================================
# 3. Java 버전 감지 및 동적 옵션 할당
# ==========================================
JAVA_FULL_VERSION=$(java -version 2>&1 | awk -F\" '/version/ {print $2}')
case "$JAVA_FULL_VERSION" in
  1.*) JAVA_VERSION=$(echo "$JAVA_FULL_VERSION" | cut -d. -f2) ;;
  *)   JAVA_VERSION=$(echo "$JAVA_FULL_VERSION" | cut -d. -f1) ;;
esac

if [ "$JAVA_VERSION" -ge 21 ]; then
  # [Java 21 이상 전용 설정]
  JVM_VERSION_STR="JDK 21+"
  # GC_LOG_OPTS="-Xlog:gc*:file=${LOG_DIR}/gc.log:time,uptime,level,tags:filecount=5,filesize=10M"
  GC_LOG_OPTS="" # 💡 권한 문제 해결을 위해 로그 파일 생성을 하지 않음
  NETTY_OPTS="--add-opens java.base/java.nio=ALL-UNNAMED \
              --add-opens java.base/sun.misc=ALL-UNNAMED \
              --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
              -Dio.netty.tryReflectionSetAccessible=true \
              -Djdk.nio.maxCachedBufferSize=262144"
else
  # [Java 8 레거시 전용 설정]
  JVM_VERSION_STR="JDK 8"
  GC_LOG_OPTS="-verbose:gc -Xloggc:${LOG_DIR}/gc.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=10M"
  NETTY_OPTS=""
fi

# 4. 환경(dev/prd)별 프로필 (✅ 여기서 spring.profiles.active 가 주입됨)
if [ "$ENV_TYPE" = "prd" ]; then
  BASE_ENV_OPTS="-Dspring.profiles.active=prd -Dio.netty.leakDetection.level=ADVANCED"
else
  BASE_ENV_OPTS="-Dspring.profiles.active=dev -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${LOG_DIR}/heapdump.hprof -Dio.netty.leakDetection.level=PARANOID"
fi

# 5. 스크립트 내부 기본 옵션 묶기
BASE_JAVA_OPTS="$COMMON_OPTS $LOG_OPTS $GC_LOG_OPTS $NETTY_OPTS $BASE_ENV_OPTS"

# 6. 최종 병합 (k8s의 JAVA_OPTS로 메모리와 관련설정을 덮어씀)
export FINAL_JAVA_OPTS="$BASE_JAVA_OPTS $JAVA_OPTS"

echo "=================================================="
echo "🚀 Starting Zefio (K8s Mode: $ENV_TYPE)"
echo "☕ Detected Java Version: $JAVA_FULL_VERSION ($JVM_VERSION_STR)"
echo "📂 Log Directory: $LOG_DIR"
echo "⚙️ K8s Custom OPTS (Memory & EAI): $JAVA_OPTS"
echo "⚙️ FINAL_JAVA_OPTS: $FINAL_JAVA_OPTS"
echo "=================================================="

# 7. 실행
exec java $FINAL_JAVA_OPTS -cp "$CLASSPATH" io.zefio.launcher.ZefioApplication
