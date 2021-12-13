#!/bin/sh

BASEDIR=$(dirname "$0")
. "${BASEDIR}/functions.sh"
"$(dockerCompose)" down
docker volume rm su-se-bakover_supstonad-db-local
"$(dockerCompose)" up -d
