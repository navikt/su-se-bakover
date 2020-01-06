package no.nav.su.se.bakover

import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

const val origin = "host.server.no"
const val oidcGroupUuid = "ENLANG-AZURE-OBJECT-GRUPPE-ID"
const val clientId = "clientId"
const val oidcConfigUrl = "http://localhost/config"

fun testEnvironment() = Environment(
        allowCorsOrigin = origin,
        oidcClientId = clientId,
        oidcRequiredGroup = oidcGroupUuid,
        oidcConfigUrl = oidcConfigUrl
)

fun testServer(test: ApplicationEngine.() -> Unit) = embeddedServer(Netty, 8088) {
    testApp(testEnvironment())
}.apply {
    val stopper = GlobalScope.launch {
        delay(10000)
        stop(0, 0, TimeUnit.SECONDS)
    }
    start(wait = false)
    try {
        test()
    } finally {
        stopper.cancel()
        stop(0, 0, TimeUnit.SECONDS)
    }
}
