package no.nav.su.se.bakover.client.proxy

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.ClientsConfig.SuProxyConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import org.slf4j.LoggerFactory

interface SUProxyClient {
    fun ping()
}

class SuProxyClientStub : SUProxyClient {
    override fun ping() {
        println("Pong")
    }
}

class SUProxyClientImpl(
    val config: SuProxyConfig,
    val azure: AzureAd,
) : SUProxyClient {
    private val log = LoggerFactory.getLogger(this::class.java)
    override fun ping() {
        val (_, response, result) =
            "${config.url}/ping"
                .httpGet()
                .authentication().bearer(azure.getSystemToken(config.clientId))
                .header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                .header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                .header("Nav-Call-Id", getOrCreateCorrelationIdFromThreadLocal())
                .responseString()

        result.fold(
            success = { body ->
                log.info("SUProxy ping OK: status=${response.statusCode}, body=$body")
            },
            failure = { error ->
                log.error(
                    "SUProxy ping failed: status=${response.statusCode}, message=${error.message}",
                    error,
                )
            },
        )
    }
}
