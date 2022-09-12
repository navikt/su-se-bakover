#!/bin/bash
./gradlew spotlessApply build -Pskip-heavy-infrastructure-tests=true "$@"
