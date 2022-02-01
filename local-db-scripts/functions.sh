#!/bin/sh

dockerCompose() {
  if [ -x "$(docker compose ls 2> /dev/null)" ];
  then
    echo "docker compose"
  else
    # Docker hasn't released docker engine with builtin compose yet.
    echo "docker-compose"
  fi
}
