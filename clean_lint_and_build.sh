#!/bin/bash
./gradlew clean formatKotlin build -Pskip-heavy-infrastructure-tests=true
