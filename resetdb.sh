#!/bin/sh

if [ -x "$(docker compose ls 2> /dev/null)" ];
then
  docker compose down
  docker volume rm su-se-bakover_supstonad-db-local
  docker compose up -d
else
  # Docker hasn't released docker engine with builtin compose yet.
  docker-compose down
  docker volume rm su-se-bakover_supstonad-db-local
  docker-compose up -d
fi
