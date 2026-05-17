#!/bin/sh
# ==============================================================================
# [Zefio 알람 정책 및 모니터링 가이드]
#
# 1. Critical (치명적 오류 - 알람 발생 대상)
#    - 시스템의 정상 운영이 불가능하거나 즉각적인 서버 확장이 필요한 상태
#    - [자원/연결 고갈] SYSTEM_BUSY, QUEUE_CAPACITY_EXCEEDED, CONNECTION_POOL_EXHAUSTED
#    - [인프라/설정] DATABASE_TIMEOUT, SSL_HANDSHAKE_ERROR
#    - [JVM/OS 레벨] OutOfMemoryError, StackOverflowError 등 프로세스 생존 위협 오류
#    ※ 과도한 알람 폭주(Log Spam)를 방지하기 위해 1분(60초)간 동일 오류 알람이 제한됩니다.
#
# 2. ERROR (비치명적 오류 - 실시간 모니터링 대상)
#    - 전체 시스템 장애는 아니나, 개별 거래 실패나 일시적 지연을 유발하는 오류
#    - [네트워크 지연] NETWORK_ERROR, READ_TIMEOUT 등 (자동 재시도 대상)
#    - [클라이언트 오류] BAD_REQUEST, UNAUTHORIZED 등 (HTTP 4xx 계열)
#    - [서버/연동 오류] INTERNAL_SERVER_ERROR, REMOTE_SERVER_ERROR 등 (HTTP 5xx 계열)
#    - [비즈니스/기타] TIMEOUT, DUPLICATE_REQUEST, 개별 필터 오류 등
# ==============================================================================

# [대시보드 노출 옵션 설정]
SHOW_JVM="true"
SHOW_CONN_POOL="true"
SHOW_THREAD_POOL="true"
SHOW_INTERNAL_QUEUE="true"
SHOW_FILTER_METRICS="true"
SHOW_NON_CRITICAL="true"
SHOW_REALTIME="true"
SHOW_REALTIME_ERR="true"

# [표시 및 포맷 설정]
SHOW_RECENT_ROWS=5
SHOW_ERROR_ROWS=5
TIME_UNIT="ms"

BIN_DIR="$(cd "$(dirname "$0")" && pwd)"
[ -f "$BIN_DIR/env.sh" ] && . "$BIN_DIR/env.sh" >/dev/null 2>&1

# 🚀 [수정] sed 제거 -> awk를 이용한 안전한 추출
YAML_FILENAME=$(awk -F'classpath:/' '/spring.config.location=classpath:\// { split($2, a, /[" ]/); print a[1]; exit }' "$BIN_DIR/run.sh" 2>/dev/null)
YAML_FILE="${BASE_DIR}/resources/${YAML_FILENAME:-application.yaml}"

RED='\033[1;91m'
GREEN='\033[1;92m'
YELLOW='\033[1;93m'
CYAN='\033[1;96m'
BOLD='\033[1m'
RESET='\033[0m'

OS_TYPE=$(uname -s)
if [ "$OS_TYPE" = "Linux" ]; then
    ENV_LABEL="Linux"; [ -f /etc/os-release ] && ENV_LABEL=$(awk -F'"' '/^PRETTY_NAME/ {print $2}' /etc/os-release)
    CLEAR_CMD="clear"; export LANG=ko_KR.UTF-8
elif [ "$OS_TYPE" = "AIX" ]; then
    ENV_LABEL="AIX $(oslevel 2>/dev/null)"; CLEAR_CMD="tput clear"; unset LC_ALL; export LANG=ko_KR.UTF-8
else
    ENV_LABEL="${OS_TYPE:-Unknown}"; CLEAR_CMD="clear"; export LANG=ko_KR.UTF-8
fi

trap "printf '\n${YELLOW}모니터링을 종료합니다...${RESET}\n'; exit" EXIT SIGINT SIGTERM

SERVER_PORT=$(awk -F':' '/^server:[[:space:]]*$/ { in_server=1; next } /^[^[:space:]#]/ { in_server=0 } in_server && /^[[:space:]]+port:/ { gsub(/[^0-9]/, "", $2); print $2; exit }' "$YAML_FILE" 2>/dev/null)
SERVER_PORT="${SERVER_PORT:-8080}"
METRICS_URL="http://localhost:${SERVER_PORT}/actuator/prometheus"
API_BASE_URL="http://localhost:${SERVER_PORT}/api/monitor"
INTERVAL=5

fetch_metrics() {
    if command -v curl >/dev/null 2>&1; then
        curl -s --connect-timeout 2 "$METRICS_URL"
    else
        ( echo "GET /actuator/prometheus HTTP/1.1"; echo "Host: localhost"; echo "Connection: close"; echo ""; sleep 1 ) | telnet localhost "$SERVER_PORT" 2>/dev/null | awk 'BEGIN{body=0} /^\r?$/{body=1; next} {if(body) print}'
    fi
}

AWK_MULTIBYTE_SUPPORT=$(echo "가나" | awk '{
    c = $0;
    gsub(/[\x00-\x7F]/, "", c);
    if (length(c) == 2) print "true"; else print "false";
}' 2>/dev/null)

while true
do
    METRICS_DATA=$(fetch_metrics)

    APP_PID=""
    if [ -n "$APP_NAME" ]; then APP_PID=$(ps -ef | awk -v app="$APP_NAME" '/java/ && $0 ~ app && !/awk/ {print $2; exit}'); fi

    if [ -n "$APP_PID" ] && [ -n "$METRICS_DATA" ]; then STATUS_STR="${GREEN}▶ 정상 가동중${RESET} (PID: $APP_PID)"
    else STATUS_STR="${RED}■ 중단됨${RESET} (STOPPED)"; fi

    $CLEAR_CMD
    printf "${CYAN}================================================================================================${RESET}\n"
    if [ "$AWK_MULTIBYTE_SUPPORT" = "true" ]; then ENGINE_INFO="Advanced Engine (In-Memory)"; else ENGINE_INFO="Safe Mode Engine (In-Memory)"; fi
    printf "  ${BOLD}연계 시스템 (Zefio) 통합 대시보드${RESET} (OS: $ENV_LABEL, UI: $ENGINE_INFO)\n"
    printf "  상태 : ${STATUS_STR} | 포트: $SERVER_PORT | 시간: $(date +'%Y-%m-%d %H:%M:%S')\n"
    printf "${CYAN}================================================================================================${RESET}\n"

    if [ -z "$METRICS_DATA" ]; then
        printf "${RED}  [오류] 메트릭 포트($SERVER_PORT)에 연결할 수 없습니다.${RESET}\n"
        sleep $INTERVAL; continue
    fi

    AWK_PARSE_BLOCK='
        gsub(/,/, " ");
        if (NF < 2 || substr($1,1,1) == "#") next;
        val = $NF;
        idx = index($0, "{");
        if (idx > 0) metric = substr($0, 1, idx - 1);
        else metric = $1;

        fName = get_attr($0, "flowName"); fLabel = get_attr($0, "flowLabel");
        nName = get_attr($0, "moduleName"); nLabel = get_attr($0, "moduleLabel");

        if (fName != "" && fLabel != "") map_flow[fName] = fLabel;
        if (nName != "" && nLabel != "") map_node[nName] = nLabel;

        if (metric == "jvm_heap_used_bytes") jvm_used = val;
        if (metric == "jvm_heap_max_bytes") jvm_max = val;
        if (metric == "process_cpu_usage") cpu = val;
        if (metric == "jvm_buffer_memory_used_bytes" && index($0, "id=\"direct\"") > 0) direct = val;
        if (metric == "process_uptime_seconds") uptime = val;
        if (metric == "process_files_open_files") f_open = val;
        if (metric == "process_files_max_files") f_max = val;
        if (metric == "jvm_gc_pause_seconds_max") gc_max = val;

        if (metric == "connection_pool_max") { cp_max[nName] = val; cp_fn[nName] = fName; }
        if (metric == "connection_pool_active") cp_act[nName] = val;
        if (metric == "connection_pool_idle") cp_idl[nName] = val;

        if (metric == "threadpool_active_threads") tp_act[fName] = val;
        if (metric == "threadpool_pool_size") tp_pool[fName] = val;

        if (metric == "netty_threads_active") netty_act[nName] = val;
        if (metric == "link_netty_threads") netty_pool[nName] = val;
        if (metric == "netty_pending_tasks") netty_pend[nName] = val;

        if (metric == "queue_capacity") { qk = fName"\034"nName; q_cap[qk] = val; q_fName[qk] = fName; q_nName[qk] = nName; }
        if (metric == "queue_size") { qk = fName"\034"nName; q_size[qk] = val; }

        if (metric == "link_module_accepted") { k=fName"\034"nName; fm_acc[k] = val; fm_f[k] = fName; fm_n[k] = nName; }
        if (metric == "link_module_failed") { if (val > total_err_count) total_err_count = val; }
        if (metric == "module_tps") { k=fName"\034"nName; fm_tps[k] = val; }
        if (metric == "module_exec_avg_ms") { k=fName"\034"nName; fm_avg[k] = val; }
        if (metric == "module_exec_max_ms") { k=fName"\034"nName; fm_avg[k] = val; fm_max[k] = val; }
    '

    if [ "$AWK_MULTIBYTE_SUPPORT" = "true" ]; then
        AWK_CORE_SCRIPT='
        BEGIN { c_res="\033[0m"; c_warn="\033[1;93m"; c_crit="\033[1;91m"; c_info="\033[1;96m"; c_ok="\033[1;92m"; total_err_count=0; }
        function get_attr(str, attr,   _k, _idx, _s) { _k = attr "=\""; _idx = index(str, _k); if (_idx == 0) return ""; _s = substr(str, _idx + length(_k)); return substr(_s, 1, index(_s, "\"") - 1); }
        function disp_len(s,   _c, _ascii) { _c = s; _ascii = gsub(/[\x00-\x7F]/, "", _c); return _ascii + (length(_c) * 2); }
        function rpad(s, w,   _pad, _ret) { _pad = w - disp_len(s); _ret = s; while(_pad > 0) { _ret = _ret " "; _pad--; } return _ret; }
        function lpad(s, w,   _pad, _ret) { _pad = w - disp_len(s); _ret = ""; while(_pad > 0) { _ret = _ret " "; _pad--; } return _ret s; }
        function trunc(s, max_w,   _ret, _cur, _i, _ch, _cw) { if (disp_len(s) <= max_w) return s; _ret = ""; _cur = 0; for(_i=1; _i<=length(s); _i++) { _ch = substr(s, _i, 1); _cw = (_ch ~ /[\x00-\x7F]/) ? 1 : 2; if (_cur + _cw > max_w - 2) break; _ret = _ret _ch; _cur += _cw; } return _ret ".."; }
        { '"$AWK_PARSE_BLOCK"' }
        END {
            if (show_jvm == "true") {
                print "\n" c_info "[1] 🖥️ JVM 및 시스템 자원 상태" c_res
                print "  --------------------------------------------------------------------------------"
                h_per = (jvm_max > 0) ? (jvm_used/jvm_max)*100 : 0;
                printf "  힙 메모리  : %-8.1f / %-8.1f MB (%.1f%%) | 가동시간: %.1f 분\n", jvm_used/1048576, jvm_max/1048576, h_per, uptime/60;
                printf "  직접 메모리: %-8.1f MB                | CPU 사용: %.1f%%\n", direct/1048576, cpu*100;
                printf "  파일 디스크립터: %-8d / %-8d   | 최대 GC : %.3f %s\n", int(f_open), int(f_max), (unit=="sec")?gc_max:gc_max*1000, unit;
            }
            if (show_cp == "true") {
                has_cp = 0; for(n in cp_max) { has_cp = 1; break; }
                if (has_cp) {
                    print "\n" c_info "[2] 🌐 대외 연결 세션 (Connection Pool) 상태" c_res
                    print "  --------------------------------------------------------------------------------"
                    print "  " rpad("업무(Flow)", 18) " | " rpad("모듈(Filter)", 18) " | " rpad("활성(Act)", 9) " | " rpad("대기(Idl)", 9) " | " rpad("최대(Max)", 9)
                    print "  -------------------|--------------------|-----------|-----------|-----------"
                    for (n in cp_max) {
                        if (cp_max[n] <= 0) continue;
                        fL = (cp_fn[n] in map_flow) ? map_flow[cp_fn[n]] : cp_fn[n]; nL = (n in map_node) ? map_node[n] : n;
                        col = (cp_act[n]/cp_max[n] >= 0.9) ? c_warn : c_res;
                        printf "%s  %s | %s | %s | %s | %s%s\n", col, rpad(trunc(fL,18),18), rpad(trunc(nL,18),18), lpad(int(cp_act[n]),9), lpad(int(cp_idl[n]),9), lpad(int(cp_max[n]),9), c_res;
                    }
                }
            }
            if (show_tp == "true") {
                has_tp = 0; for(f in tp_pool) { has_tp = 1; break; }
                has_netty = 0; for(n in netty_pend) { has_netty = 1; break; }
                if (has_tp || has_netty) {
                    print "\n" c_info "[3] ⚙️ 스레드 풀 및 비동기 처리" c_res
                    print "  --------------------------------------------------------------------------------"
                    print "  " rpad("분류(Flow/Type)", 24) " | " rpad("실행(Act)", 10) " | " rpad("크기(Pool)", 10) " | " rpad("대기(Pending)", 15)
                    print "  -------------------------|------------|------------|----------------"
                    for (f in tp_pool) {
                        fL = (f in map_flow) ? map_flow[f] : f;
                        printf "  %s | %s | %s | %s\n", rpad(trunc(fL" (스레드)",24),24), lpad(int(tp_act[f]),10), lpad(int(tp_pool[f]),10), lpad("-",15);
                    }
                    if (has_tp && has_netty) print "  -------------------------|------------|------------|----------------"
                    for (n in netty_pend) {
                        nL = (n in map_node) ? map_node[n] : n;
                        col = (netty_pend[n] > 0) ? c_warn : c_res;
                        act = (n in netty_act) ? int(netty_act[n]) : 0;
                        pool = (n in netty_pool) ? int(netty_pool[n]) : 0;
                        printf "%s  %s | %s | %s | %s%s\n", col, rpad(trunc(nL" (Netty)",24),24), lpad(act,10), lpad(pool,10), lpad(int(netty_pend[n]),15), c_res;
                    }
                }
            }
            if (show_q == "true") {
                has_q = 0; for(f in q_cap) { has_q = 1; break; }
                if (has_q) {
                    print "\n" c_info "[4] 🗄️ 구간별 내부 대기열 (CPU / IO Queue) 상태" c_res
                    print "  --------------------------------------------------------------------------------"
                    print "  " rpad("업무(Flow)", 18) " | " rpad("큐종류(Type)", 12) " | " rpad("대기건수", 12) " | " rpad("최대수용량(Cap)", 14)
                    print "  -------------------|--------------|--------------|----------------"
                    for (qk in q_cap) {
                        if (q_cap[qk] <= 0) continue;
                        fn = q_fName[qk]; nn = q_nName[qk];
                        fL = (fn in map_flow) ? map_flow[fn] : fn;
                        col = (q_size[qk]/q_cap[qk] >= 0.8) ? c_warn : c_res;
                        printf "%s  %s | %s | %s | %s%s\n", col, rpad(trunc(fL,18),18), rpad(trunc(nn,12),12), lpad(int(q_size[qk]),12), lpad(int(q_cap[qk]),14), c_res;
                    }
                }
            }
            if (show_fm == "true") {
                has_fm = 0; for(k in fm_acc) { has_fm = 1; break; }
                if (has_fm) {
                    print "\n" c_info "[5] 📊 구간별 처리 통계 (Filter Metrics)" c_res
                    print "  --------------------------------------------------------------------------------"
                    max_flen = 16; max_nlen = 18;
                    for (k in fm_acc) {
                        fL = (fm_f[k] in map_flow) ? map_flow[fm_f[k]] : fm_f[k];
                        nL = (fm_n[k] in map_node) ? map_node[fm_n[k]] : fm_n[k];
                        if (disp_len(fL) > max_flen) max_flen = disp_len(fL);
                        if (disp_len(nL) > max_nlen) max_nlen = disp_len(nL);
                    }
                    if (max_flen > 30) max_flen = 30;
                    if (max_nlen > 30) max_nlen = 30;

                    sep_f = ""; for(i=1;i<=max_flen+1;i++) sep_f=sep_f"-";
                    sep_n = ""; for(i=1;i<=max_nlen+1;i++) sep_n=sep_n"-";

                    print "  " rpad("업무(Flow)", max_flen) " | " rpad("모듈(Filter)", max_nlen) " | " rpad("누적(건)", 8) " | " rpad("초당(TPS)", 9) " | " rpad("평균(" unit ")", 11) " | " rpad("최대(" unit ")", 11)
                    print "  " sep_f "|" sep_n "|----------|-----------|-------------|-------------"
                    for (k in fm_acc) {
                        fL = (fm_f[k] in map_flow) ? map_flow[fm_f[k]] : fm_f[k]; nL = (fm_n[k] in map_node) ? map_node[fm_n[k]] : fm_n[k];
                        a_disp = (unit=="sec") ? sprintf("%.3f", fm_avg[k]/1000) : sprintf("%.1f", fm_avg[k]);
                        m_disp = (unit=="sec") ? sprintf("%.3f", fm_max[k]/1000) : sprintf("%.1f", fm_max[k]);
                        printf "  %s | %s | %s | %s | %s | %s\n", rpad(trunc(fL,max_flen),max_flen), rpad(trunc(nL,max_nlen),max_nlen), lpad(int(fm_acc[k]),8), lpad(sprintf("%.2f", fm_tps[k]),9), lpad(a_disp,11), lpad(m_disp,11);
                    }
                }
            }
            out = "===MAP==="; for(k in map_flow) out = out k "=" map_flow[k] "|"; print out;
            print "===TOTAL_FAIL===" int(total_err_count);
        }'
    else
        AWK_CORE_SCRIPT='
        BEGIN { c_res="\033[0m"; c_warn="\033[1;93m"; c_crit="\033[1;91m"; c_info="\033[1;96m"; c_ok="\033[1;92m"; total_err_count=0; }
        function get_attr(str, attr,   _k, _idx, _s) { _k = attr "=\""; _idx = index(str, _k); if (_idx == 0) return ""; _s = substr(str, _idx + length(_k)); return substr(_s, 1, index(_s, "\"") - 1); }
        { '"$AWK_PARSE_BLOCK"' }
        END {
            if (show_jvm == "true") {
                print "\n" c_info "[1] 🖥️ JVM 및 시스템 자원 상태" c_res
                print "  --------------------------------------------------------------------------------"
                h_per = (jvm_max > 0) ? (jvm_used/jvm_max)*100 : 0;
                printf "  힙 메모리  : %-8.1f / %-8.1f MB (%.1f%%) | 가동시간: %.1f 분\n", jvm_used/1048576, jvm_max/1048576, h_per, uptime/60;
                printf "  직접 메모리: %-8.1f MB                | CPU 사용: %.1f%%\n", direct/1048576, cpu*100;
                printf "  파일 디스크립터: %-8d / %-8d   | 최대 GC : %.3f %s\n", int(f_open), int(f_max), (unit=="sec")?gc_max:gc_max*1000, unit;
            }
            if (show_cp == "true") {
                has_cp = 0; for(n in cp_max) { has_cp = 1; break; }
                if (has_cp) {
                    print "\n" c_info "[2] 🌐 대외 연결 세션 (Connection Pool) 상태" c_res
                    print "  --------------------------------------------------------------------------------"
                    print "  업무(Flow)       | 모듈(Filter)       | 활성(Act)| 대기(Idl)| 최대(Max)"
                    print "  -----------------|--------------------|----------|----------|----------"
                    for (n in cp_max) {
                        if (cp_max[n] <= 0) continue;
                        fL = (cp_fn[n] in map_flow) ? map_flow[cp_fn[n]] : cp_fn[n]; nL = (n in map_node) ? map_node[n] : n;
                        col = (cp_act[n]/cp_max[n] >= 0.9) ? c_warn : c_res;
                        printf "%s  %-16s | %-18s | %8d | %8d | %8d%s\n", col, fL, nL, int(cp_act[n]), int(cp_idl[n]), int(cp_max[n]), c_res;
                    }
                }
            }
            if (show_tp == "true") {
                has_tp = 0; for(f in tp_pool) { has_tp = 1; break; }
                has_netty = 0; for(n in netty_pend) { has_netty = 1; break; }
                if (has_tp || has_netty) {
                    print "\n" c_info "[3] ⚙️ 스레드 풀 및 비동기 처리" c_res
                    print "  --------------------------------------------------------------------------------"
                    print "  분류(Flow/Type)          | 실행(Act)  | 크기(Pool) | 대기(Pending)"
                    print "  -------------------------|------------|------------|----------------"
                    for (f in tp_pool) {
                        fL = (f in map_flow) ? map_flow[f] : f;
                        printf "  %-24s | %10d | %10d | %15s\n", fL" (스레드)", int(tp_act[f]), int(tp_pool[f]), "-";
                    }
                    if (has_tp && has_netty) print "  -------------------------|------------|------------|----------------"
                    for (n in netty_pend) {
                        nL = (n in map_node) ? map_node[n] : n;
                        col = (netty_pend[n] > 0) ? c_warn : c_res;
                        act = (n in netty_act) ? int(netty_act[n]) : 0;
                        pool = (n in netty_pool) ? int(netty_pool[n]) : 0;
                        printf "%s  %-24s | %10d | %10d | %15d%s\n", col, nL" (Netty)", act, pool, int(netty_pend[n]), c_res;
                    }
                }
            }
            if (show_q == "true") {
                has_q = 0; for(f in q_cap) { has_q = 1; break; }
                if (has_q) {
                    print "\n" c_info "[4] 🗄️ 구간별 내부 대기열 (CPU / IO Queue) 상태" c_res
                    print "  --------------------------------------------------------------------------------"
                    print "  업무(Flow)         | 큐종류(Type) | 대기건수     | 최대수용량(Cap)"
                    print "  -------------------|--------------|--------------|----------------"
                    for (qk in q_cap) {
                        if (q_cap[qk] <= 0) continue;
                        fn = q_fName[qk]; nn = q_nName[qk];
                        fL = (fn in map_flow) ? map_flow[fn] : fn;
                        col = (q_size[qk]/q_cap[qk] >= 0.8) ? c_warn : c_res;
                        printf "%s  %-18s | %-12s | %12d | %14d%s\n", col, fL, nn, int(q_size[qk]), int(q_cap[qk]), c_res;
                    }
                }
            }
            if (show_fm == "true") {
                has_fm = 0; for(k in fm_acc) { has_fm = 1; break; }
                if (has_fm) {
                    print "\n" c_info "[5] 📊 구간별 처리 통계 (Filter Metrics)" c_res
                    print "  --------------------------------------------------------------------------------"
                    max_flen = 16; max_nlen = 18;
                    for (k in fm_acc) {
                        fL = (fm_f[k] in map_flow) ? map_flow[fm_f[k]] : fm_f[k];
                        nL = (fm_n[k] in map_node) ? map_node[fm_n[k]] : fm_n[k];
                        if (length(fL) > max_flen) max_flen = length(fL);
                        if (length(nL) > max_nlen) max_nlen = length(nL);
                    }
                    if (max_flen > 30) max_flen = 30;
                    if (max_nlen > 30) max_nlen = 30;

                    fmt_head = "  %-" max_flen "s | %-" max_nlen "s | %8s | %8s | %8s | %8s\n";
                    printf fmt_head, "흐름(Flow)", "모듈(Filter)", "누적건수", "TPS", "평균(ms)", "최대(ms)";
                    sep_f = ""; for(i=1;i<=max_flen+1;i++) sep_f=sep_f"-";
                    sep_n = ""; for(i=1;i<=max_nlen+1;i++) sep_n=sep_n"-";
                    print "  " sep_f "|" sep_n "|----------|----------|----------|----------"

                    fmt_row = "  %-" max_flen "s | %-" max_nlen "s | %8d | %8.2f | %8.1f | %8.1f\n";
                    for (k in fm_acc) {
                        fL = (fm_f[k] in map_flow) ? map_flow[fm_f[k]] : fm_f[k]; nL = (fm_n[k] in map_node) ? map_node[fm_n[k]] : fm_n[k];
                        a_d = (unit=="sec") ? fm_avg[k]/1000 : fm_avg[k]; m_d = (unit=="sec") ? fm_max[k]/1000 : fm_max[k];
                        printf fmt_row, substr(fL,1,max_flen), substr(nL,1,max_nlen), int(fm_acc[k]), fm_tps[k], a_d, m_d;
                    }
                }
            }
            out = "===MAP==="; for(k in map_flow) out = out k "=" map_flow[k] "|"; print out;
            print "===TOTAL_FAIL===" int(total_err_count);
        }'
    fi

    # 🚀 순수 awk 파이프라인 구동
    AWK_RESULT=$(printf "%s\n" "$METRICS_DATA" | awk -v unit="$TIME_UNIT" -v show_jvm="$SHOW_JVM" -v show_cp="$SHOW_CONN_POOL" -v show_tp="$SHOW_THREAD_POOL" -v show_q="$SHOW_INTERNAL_QUEUE" -v show_fm="$SHOW_FILTER_METRICS" "$AWK_CORE_SCRIPT")

    # 🚀 화면 출력
    printf "%s\n" "$AWK_RESULT" | awk '!/^===MAP===/ && !/^===TOTAL_FAIL===/ {print}'

    # 🚀 메타데이터 추출
    LABEL_MAP_STR=$(printf "%s\n" "$AWK_RESULT" | awk '/^===MAP===/ {print substr($0, 10)}')
    TOTAL_ERR_COUNT=$(printf "%s\n" "$AWK_RESULT" | awk '/^===TOTAL_FAIL===/ {print substr($0, 17)}')
    [ -z "$TOTAL_ERR_COUNT" ] && TOTAL_ERR_COUNT=0

    # 🚀 API 호출: 인메모리 버퍼 조회
    RECENT_ERRORS=$(curl -s --connect-timeout 1 "${API_BASE_URL}/recent-errors?limit=50")
    RECENT_STATS=$(curl -s --connect-timeout 1 "${API_BASE_URL}/recent-stats?limit=50")
    RECENT_ERR_STATS=$(curl -s --connect-timeout 1 "${API_BASE_URL}/recent-err-stats?limit=50")

    # [6] 치명적 오류 (FATAL)
    printf "\n${CYAN}[6] 🚨 치명적 오류 (Critical Alert Push)${RESET}\n"
    printf "  --------------------------------------------------------------------------------\n"
    if [ -n "$RECENT_ERRORS" ]; then
        FATAL_ERRS=$(echo "$RECENT_ERRORS" | awk '/\[FATAL\]/')
        if [ -n "$FATAL_ERRS" ]; then
            echo "$FATAL_ERRS" | tail -n "$SHOW_ERROR_ROWS" | awk -v col="${RED}" -v rst="${RESET}" '{ print col "  " $0 rst; }'
        else
            printf "  ${GREEN}현재까지 감지된 치명적 오류가 없습니다.${RESET}\n"
        fi
    else
        printf "  ${GREEN}현재까지 감지된 치명적 오류가 없습니다.${RESET}\n"
    fi

    # [7] 일반 오류 (ERROR)
    if [ "$SHOW_NON_CRITICAL" = "true" ]; then
        printf "\n${CYAN}[7] ⚠️ 일반 경고 및 오류 (ERROR)${RESET}\n"
        printf "  --------------------------------------------------------------------------------\n"
        if [ -n "$RECENT_ERRORS" ]; then
            NORMAL_ERRS=$(echo "$RECENT_ERRORS" | awk '/\[ERROR\]/')
            if [ -n "$NORMAL_ERRS" ]; then
                echo "$NORMAL_ERRS" | tail -n "$SHOW_ERROR_ROWS" | awk -v col="${YELLOW}" -v rst="${RESET}" '{ print col "  " $0 rst; }'
            else
                printf "  ${GREEN}최근 발생한 비치명적 시스템 에러 내역이 없습니다.${RESET}\n"
            fi
        else
            printf "  ${GREEN}최근 발생한 비치명적 시스템 에러 내역이 없습니다.${RESET}\n"
        fi
    fi

    # [8] & [9] 실시간 거래 내역 통계 처리용 AWK 스크립트 정의
    if [ "$AWK_MULTIBYTE_SUPPORT" = "true" ]; then
        AWK_STAT_SCRIPT='
        BEGIN {
            split(labels, pairs, "|"); for(i in pairs){ split(pairs[i], kv, "="); if(kv[1]!="") map[kv[1]]=kv[2]; }
            c_res="\033[0m"; c_warn="\033[1;93m"; c_crit="\033[1;91m";
            print "  " rpad("시간",12) " | " rpad("추적ID(TID)",36) " | " rpad("업무(Flow)",16) " | " rpad("결과",4) " | " rpad("총소요(" unit ")",11) " | " rpad("처리(" unit ")",11) " | " rpad("대외(" unit ")",11)
            print "  -------------|--------------------------------------|------------------|------|-------------|-------------|-------------"
        }
        function disp_len(s,   _c, _ascii) { _c = s; _ascii = gsub(/[\x00-\x7F]/, "", _c); return _ascii + (length(_c) * 2); }
        function rpad(s, w,   _pad, _ret) { _pad = w - disp_len(s); _ret = s; while(_pad > 0) { _ret = _ret " "; _pad--; } return _ret; }
        function lpad(s, w,   _pad, _ret) { _pad = w - disp_len(s); _ret = ""; while(_pad > 0) { _ret = _ret " "; _pad--; } return _ret s; }
        function trunc(s, max_w,   _ret, _cur, _i, _ch, _cw) { if (disp_len(s) <= max_w) return s; _ret = ""; _cur = 0; for(_i=1; _i<=length(s); _i++) { _ch = substr(s, _i, 1); _cw = (_ch ~ /[\x00-\x7F]/) ? 1 : 2; if (_cur + _cw > max_w - 2) break; _ret = _ret _ch; _cur += _cw; } return _ret ".."; }
        {
            time_val=$2; tid=$3; flow_key=$4; txn_res=$5; total=$6; p=$8; r=$9; gsub(/\r/, "", r);
            flow = (flow_key in map) ? map[flow_key] : flow_key;
            c=c_res; if (txn_res != "OK") c=c_crit; else if (total >= 3000) c=c_warn;
            if (unit == "sec") { t_d=sprintf("%.3f", total/1000); p_d=sprintf("%.3f", p/1000); r_d=sprintf("%.3f", r/1000); } else { t_d=total; p_d=p; r_d=r; }
            printf "%s  %s | %-36s | %s | %s | %s | %s | %s%s\n", c, rpad(time_val,12), tid, rpad(trunc(flow, 16), 16), rpad(txn_res, 4), lpad(t_d,11), lpad(p_d,11), lpad(r_d,11), c_res;
        }'
        AWK_STAT_ERR_SCRIPT='
        BEGIN {
            split(labels, pairs, "|"); for(i in pairs){ split(pairs[i], kv, "="); if(kv[1]!="") map[kv[1]]=kv[2]; }
            c_res="\033[0m"; c_crit="\033[1;91m";
            print "  " rpad("시간",12) " | " rpad("추적ID(TID)",36) " | " rpad("업무(Flow)",16) " | " rpad("결과",4) " | " rpad("총소요(" unit ")",11) " | " rpad("처리(" unit ")",11) " | " rpad("대외(" unit ")",11)
            print "  -------------|--------------------------------------|------------------|------|-------------|-------------|-------------"
        }
        function disp_len(s,   _c, _ascii) { _c = s; _ascii = gsub(/[\x00-\x7F]/, "", _c); return _ascii + (length(_c) * 2); }
        function rpad(s, w,   _pad, _ret) { _pad = w - disp_len(s); _ret = s; while(_pad > 0) { _ret = _ret " "; _pad--; } return _ret; }
        function lpad(s, w,   _pad, _ret) { _pad = w - disp_len(s); _ret = ""; while(_pad > 0) { _ret = _ret " "; _pad--; } return _ret s; }
        function trunc(s, max_w,   _ret, _cur, _i, _ch, _cw) { if (disp_len(s) <= max_w) return s; _ret = ""; _cur = 0; for(_i=1; _i<=length(s); _i++) { _ch = substr(s, _i, 1); _cw = (_ch ~ /[\x00-\x7F]/) ? 1 : 2; if (_cur + _cw > max_w - 2) break; _ret = _ret _ch; _cur += _cw; } return _ret ".."; }
        {
            time_val=$2; tid=$3; flow_key=$4; txn_res=$5; total=$6; p=$8; r=$9; gsub(/\r/, "", r);
            flow = (flow_key in map) ? map[flow_key] : flow_key;
            if (unit == "sec") { t_d=sprintf("%.3f", total/1000); p_d=sprintf("%.3f", p/1000); r_d=sprintf("%.3f", r/1000); } else { t_d=total; p_d=p; r_d=r; }
            printf "%s  %s | %-36s | %s | %s | %s | %s | %s%s\n", c_crit, rpad(time_val,12), tid, rpad(trunc(flow, 16), 16), rpad(txn_res, 4), lpad(t_d,11), lpad(p_d,11), lpad(r_d,11), c_res;
        }'
    else
        AWK_STAT_SCRIPT='
        BEGIN {
            split(labels, pairs, "|"); for(i in pairs){ split(pairs[i], kv, "="); if(kv[1]!="") map[kv[1]]=kv[2]; }
            c_res="\033[0m"; c_warn="\033[1;93m"; c_crit="\033[1;91m";
            print "  시간         | ID(TID)                              | 흐름(Flow)       | 결과   | 총 소요(ms)| 내부 처리(ms)| 외부 연동(ms)"
            print "  -------------|--------------------------------------|------------------|--------|----------|-------------|-----------"
        }
        {
            time_val=$2; tid=$3; flow_key=$4; txn_res=$5; total=$6; p=$8; r=$9; gsub(/\r/, "", r);
            flow = (flow_key in map) ? map[flow_key] : flow_key;
            c=c_res; if (txn_res != "OK") c=c_crit; else if (total >= 3000) c=c_warn;
            printf "%s  %-12s | %-36s | %-16s | %-6s | %8s | %10s | %10s%s\n", c, time_val, tid, flow, txn_res, total, p, r, c_res;
        }'
        AWK_STAT_ERR_SCRIPT='
        BEGIN {
            split(labels, pairs, "|"); for(i in pairs){ split(pairs[i], kv, "="); if(kv[1]!="") map[kv[1]]=kv[2]; }
            c_res="\033[0m"; c_crit="\033[1;91m";
            print "  시간         | ID(TID)                              | 흐름(Flow)       | 결과   | 총 소요(ms)| 내부 처리(ms)| 외부 연동(ms)"
            print "  -------------|--------------------------------------|------------------|--------|----------|-------------|-----------"
        }
        {
            time_val=$2; tid=$3; flow_key=$4; txn_res=$5; total=$6; p=$8; r=$9; gsub(/\r/, "", r);
            flow = (flow_key in map) ? map[flow_key] : flow_key;
            printf "%s  %-12s | %-36s | %-16s | %-6s | %8s | %10s | %10s%s\n", c_crit, time_val, tid, flow, txn_res, total, p, r, c_res;
        }'
    fi

    # [8] 실시간 거래 내역
    if [ "$SHOW_REALTIME" = "true" ]; then
        printf "\n${CYAN}[8] 🟢 실시간 거래 내역 (최근 ${SHOW_RECENT_ROWS}건)${RESET}\n"
        printf "  --------------------------------------------------------------------------------\n"
        if [ -n "$RECENT_STATS" ]; then
            # 🚀 [보안] API 장애 시 JSON 텍스트 파싱을 막기 위한 안전망 추가
            VALID_STATS=$(echo "$RECENT_STATS" | awk '/\[STAT\]\|/')
            if [ -n "$VALID_STATS" ]; then
                echo "$VALID_STATS" | tail -n "$SHOW_RECENT_ROWS" | awk -F'|' -v labels="$LABEL_MAP_STR" -v unit="$TIME_UNIT" "$AWK_STAT_SCRIPT"
            else
                printf "  ${YELLOW}최근 발생한 거래 내역이 없습니다.${RESET}\n"
            fi
        else
            printf "  ${YELLOW}최근 발생한 거래 내역이 없습니다.${RESET}\n"
        fi
    fi

    # [9] 실시간 오류 거래 현황
    if [ "$SHOW_REALTIME_ERR" = "true" ]; then
        printf "\n${CYAN}[9] 🔴 실시간 오류 거래 현황 (최근 ${SHOW_ERROR_ROWS}건 / 누적 ${TOTAL_ERR_COUNT}건)${RESET}\n"
        printf "  --------------------------------------------------------------------------------\n"
        if [ -n "$RECENT_ERR_STATS" ]; then
            ERR_STATS=$(echo "$RECENT_ERR_STATS" | awk '/\|ERR\|/')
            if [ -n "$ERR_STATS" ]; then
                echo "$ERR_STATS" | tail -n "$SHOW_ERROR_ROWS" | awk -F'|' -v labels="$LABEL_MAP_STR" -v unit="$TIME_UNIT" "$AWK_STAT_ERR_SCRIPT"
            else
                printf "  ${GREEN}최근 발생한 거래 오류 내역이 없습니다.${RESET}\n"
            fi
        else
            printf "  ${GREEN}최근 발생한 거래 오류 내역이 없습니다.${RESET}\n"
        fi
    fi

    printf "\n${CYAN}================================================================================================${RESET}\n"
    printf "  (상태 가이드) ${GREEN}OK(정상)${RESET} | ${YELLOW}Warn(지연/병목)${RESET} | ${RED}Critical(에러/마비)${RESET}\n"
    sleep $INTERVAL
done
