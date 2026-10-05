#!/usr/bin/env bash
# 本地端到端复现脚本：启动服务 -> 挂单 -> 开盘撮合 -> 收盘竞价 -> 随机到期撮合 -> 查询。
set -euo pipefail

PORT="${PORT:-8080}"
BASE="http://localhost:${PORT}/api/auction"
JOURNAL_DIR="$(mktemp -d)/journal"

echo "== journal dir: ${JOURNAL_DIR}"
echo "== 启动服务(使用极短的收盘随机窗口便于演示)..."
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
mvn -q -f "${ROOT_DIR}/pom.xml" package -DskipTests
JAR="$(ls "${ROOT_DIR}"/target/call-auction-matching-java-*.jar | head -1)"
(exec java -jar "${JAR}" \
  --call-auction.journal-dir="${JOURNAL_DIR}" \
  --call-auction.reference-price=10.00 \
  --call-auction.close-window-start-millis=2000 \
  --call-auction.close-window-length-millis=1000 \
  --call-auction.random-seed=42 \
  --server.port="${PORT}") >/tmp/call-auction-demo.log 2>&1 &
APP_PID=$!
trap 'kill ${APP_PID} 2>/dev/null || true' EXIT

echo "== 等待服务就绪..."
for _ in $(seq 1 60); do
  if curl -s "${BASE}/phase" >/dev/null 2>&1; then break; fi
  sleep 1
done

jq_post() { curl -s -X POST -H 'Content-Type: application/json' -d "$2" "${BASE}$1"; echo; }
jq_get() { curl -s "${BASE}$1"; echo; }

echo "== 开盘竞价挂单"
jq_post /orders '{"side":"BUY","type":"LIMIT","price":10.00,"quantity":200}'
jq_post /orders '{"side":"SELL","type":"LIMIT","price":9.99,"quantity":150}'
jq_post /orders '{"side":"BUY","type":"MARKET","quantity":80}'

echo "== 不平衡指示"
jq_get /imbalance

echo "== 开盘竞价撮合"
jq_post /phase/open-match '{}'

echo "== 进入收盘竞价并挂单"
jq_post /phase/start-close '{}'
jq_post /orders '{"side":"SELL","type":"LIMIT","price":10.02,"quantity":60}'

echo "== 等待随机结束时刻并轮询..."
RESULT="null"
for _ in $(seq 1 40); do
  RESULT="$(curl -s -X POST "${BASE}/phase/close-poll")"
  echo "${RESULT}" | grep -q '"success":true' && echo "${RESULT}" | grep -qv '"data":null' && break
  sleep 0.2
done
echo "收盘撮合结果: ${RESULT}"

echo "== 成交与委托"
jq_get /trades
jq_get /phase

echo "== 演示完成，WAL 位于 ${JOURNAL_DIR}"
