#!/bin/bash
set -e

# MySQL creates MYSQL_DATABASE (eventhub_auth) and MYSQL_USER first.
# Create the remaining per-service databases and grant the local app user access.
for database in \
  eventhub_gateway \
  eventhub_event \
  eventhub_ticket \
  eventhub_order \
  eventhub_notification \
  eventhub_analytics
do
  mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" -e \
    "CREATE DATABASE IF NOT EXISTS \`${database}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON \`${database}\`.* TO '${MYSQL_USER}'@'%';"
done
