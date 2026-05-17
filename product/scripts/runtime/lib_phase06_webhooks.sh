#!/usr/bin/env bash

phase06_receiver_script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/phase06_webhook_receiver.js"

phase06_start_receiver() {
  local secret="$1"
  local received_file="$2"
  local log_file="$3"
  local port="${PHASE06_WEBHOOK_RECEIVER_PORT:-39091}"
  if test -n "${PLATFORM_CURL_CONTAINER_NETWORK:-}"; then
    docker rm -f minifin-phase06-receiver >/dev/null 2>&1 || true
    docker run --rm -v /tmp:/tmp alpine:3.20 rm -f "${received_file}" "${log_file}"
    docker run --rm --name minifin-phase06-receiver \
      --network "${PLATFORM_CURL_CONTAINER_NETWORK}" \
      -v "$(dirname "${phase06_receiver_script}"):/scripts:ro" \
      -v /tmp:/tmp \
      -e MINIFIN_WEBHOOK_SECRET="${secret}" \
      -e MINIFIN_WEBHOOK_RECEIVED_FILE="${received_file}" \
      -e MINIFIN_WEBHOOK_RECEIVER_PORT="${port}" \
      node:22-alpine node /scripts/phase06_webhook_receiver.js >"${log_file}" 2>&1 &
    PHASE06_RECEIVER_PID="$!"
    PHASE06_RECEIVER_CONTAINER="minifin-phase06-receiver"
    PHASE06_RECEIVER_URL="http://minifin-phase06-receiver:${port}/webhooks/minifin"
  else
    rm -f "${received_file}" "${log_file}"
    MINIFIN_WEBHOOK_SECRET="${secret}" \
      MINIFIN_WEBHOOK_RECEIVED_FILE="${received_file}" \
      MINIFIN_WEBHOOK_RECEIVER_PORT="${port}" \
      node "${phase06_receiver_script}" >"${log_file}" 2>&1 &
    PHASE06_RECEIVER_PID="$!"
    PHASE06_RECEIVER_URL="http://127.0.0.1:${port}/webhooks/minifin"
  fi
  for _ in $(seq 1 50); do
    if test -n "${PLATFORM_CURL_CONTAINER_NETWORK:-}"; then
      docker run --rm --network "${PLATFORM_CURL_CONTAINER_NETWORK}" curlimages/curl:8.10.1 -fsS "http://minifin-phase06-receiver:${port}/health" >/dev/null 2>&1 && return 0
    elif curl -fsS "http://127.0.0.1:${port}/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.1
  done
  cat "${log_file}" >&2 || true
  return 1
}

phase06_stop_receiver() {
  if test -n "${PHASE06_RECEIVER_CONTAINER:-}"; then
    docker rm -f "${PHASE06_RECEIVER_CONTAINER}" >/dev/null 2>&1 || true
  fi
  if test -n "${PHASE06_RECEIVER_PID:-}"; then
    kill "${PHASE06_RECEIVER_PID}" >/dev/null 2>&1 || true
    wait "${PHASE06_RECEIVER_PID}" >/dev/null 2>&1 || true
  fi
}
