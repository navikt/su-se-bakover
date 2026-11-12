# Vedlikehold

## Dependabot

Vi bruker Github sin innebygde dependabot: https://github.com/navikt/su-se-bakover/network/updates
Se og `.github/dependabot.yaml`
Denne vil opprette PRs en gang i uka på dependencies som ikke kjører siste versjon.

## Upgrade gradlew

1. Find the lastest version of gradle, e.g. by checking here: https://gradle.org/releases/
2. Then run `./gradlew wrapper --gradle-version <version>`
3. In `build.gradle.kts` you also have to change the gradle version. E.g. search for `gradleVersion =`

## Upgrade kotlin/ktor

1. in `gradle.properties` look for `ktorVersion` or `kotlinVersion`
2. Replace the version with the one you want
3. Load gradle changes

## Upgrade java version

1. In `build.gradle.kts` and search for `jvmTarget = `
2. In `.github/workflows/*.yml` and search for `java-version`
3. In `Dockerfile` replace `FROM eclipse-temurin:<version>-jre`
