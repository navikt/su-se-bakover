#!/bin/sh
BASEDIR=$(dirname "$0")
. "${BASEDIR}/functions.sh"
while ! "$(dockerCompose)" ps | grep su-se-bakover | grep healthy
do
  echo "Waiting for postgres to start. Sleeping one second ..."
  sleep 1
done
i=0
until="${1:-1}"
echo "Creating $until søknad(er)"
while [ "$i" -ne "$until" ]
do
        i=$((i+1))
        "${BASEDIR}/../gradlew" -p "${BASEDIR}/.."  nySøknad
done
