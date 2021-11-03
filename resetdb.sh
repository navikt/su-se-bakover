#!/bin/sh
docker compose down
docker volume rm su-se-bakover_supstonad-db-local
docker compose up -d

