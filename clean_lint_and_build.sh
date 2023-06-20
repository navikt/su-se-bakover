#!/bin/bash
./gradlew --no-build-cache clean spotlessApply build "$@"
