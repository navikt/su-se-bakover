#!/bin/sh
BASEDIR=$(dirname "$0")
#"${BASEDIR}/resetdb.sh"
java -classpath "${BASEDIR}/web-regresjonstest/build/classes/kotlin/test/:./web/build/libs/*:./test-common/build/classes/kotlin/main:/Users/hestad/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-server-test-host/1.6.4/7bbbae6a29139f910013cd422894971c63135599/ktor-server-test-host-jvm-1.6.4.jar" no.nav.su.se.bakover.web.søknad.OpprettNySakMedSøknadLokaltKt