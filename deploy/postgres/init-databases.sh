#!/usr/bin/env bash
set -euo pipefail

create_database() {
  local database="$1"
  local role="$2"
  local password="$3"

  psql --username "$POSTGRES_USER" --dbname postgres --set ON_ERROR_STOP=1 <<SQL
CREATE ROLE ${role} LOGIN PASSWORD '${password}';
CREATE DATABASE ${database} OWNER ${role};
REVOKE ALL ON DATABASE ${database} FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE ${database} TO ${role};
SQL
}

create_database conversation_db conversation_user "$CONVERSATION_DB_PASSWORD"
create_database orchestration_db orchestration_user "$ORCHESTRATION_DB_PASSWORD"
create_database knowledge_db knowledge_user "$KNOWLEDGE_DB_PASSWORD"
create_database evaluation_db evaluation_user "$EVALUATION_DB_PASSWORD"
create_database insight_db insight_user "$INSIGHT_DB_PASSWORD"

psql --username "$POSTGRES_USER" --dbname knowledge_db --set ON_ERROR_STOP=1 <<SQL
CREATE EXTENSION IF NOT EXISTS vector;
SQL
