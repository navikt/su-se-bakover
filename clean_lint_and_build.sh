#!/bin/bash
./gradlew clean spotlessApply build -Pskip-heavy-infrastructure-tests=true
