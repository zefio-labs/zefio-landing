FROM hypriot/rpi-java

# ✅ 작업 디렉토리 설정
WORKDIR /Zefio

# ✅ 일반 사용자 생성 (UID 1000)
RUN useradd -u 1000 -m appuser

# 💡 수정 2: 로그 디렉토리를 미리 생성하고 appuser에게 권한 부여 (로그 에러 해결)
RUN mkdir -p /Zefio/logs && chown -R appuser:appuser /Zefio

# 💡 수정됨 1: 로컬의 Zefio 폴더 내용물(lib, resources)을 컨테이너의 /Zefio/로 복사
COPY --chown=appuser:appuser ./Zefio /Zefio/

# 💡 수정됨 2: 로컬 최상위 경로에 있는 entrypoint.sh를 컨테이너의 /Zefio/로 복사
COPY --chown=appuser:appuser ./entrypoint.sh /Zefio/

# ✅ 엔트리포인트 실행 권한 부여
RUN chmod +x /Zefio/entrypoint.sh

# ✅ 사용자 전환
USER appuser

# 💡 수정됨 3: 필요한 특정 포트만 노출 (예: 8080. 필요에 따라 수정하세요)
# 기존 0-62000과 같은 광범위한 포트 범위는 라즈베리파이의 메모리를 고갈시키고 도커 데몬을 다운시킵니다.
EXPOSE 52001-52020

# ✅ 엔트리포인트 실행
ENTRYPOINT ["/Zefio/entrypoint.sh"]
