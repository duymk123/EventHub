#!/usr/bin/env bash
set -euo pipefail

topics=(
  order.paid order.failed order.refunded
  ticket.issued ticket.checked-in ticket.checked-out ticket.cancelled
  notification.dlq analytics.dlq
)

for topic in "${topics[@]}"; do
  /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server kafka:9092 \
    --create --if-not-exists \
    --topic "$topic" --partitions 3 --replication-factor 1
done
