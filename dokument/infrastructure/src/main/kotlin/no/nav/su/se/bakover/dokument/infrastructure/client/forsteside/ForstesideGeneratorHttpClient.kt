package no.nav.su.se.bakover.dokument.infrastructure.client.forsteside

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import dokument.domain.forsteside.ForstesideGeneratorClient
import dokument.domain.forsteside.KunneIkkeGenerereForsteside
import dokument.domain.forsteside.PostForstesideRequest
import dokument.domain.forsteside.PostForstesideResponse
import no.nav.su.se.bakover.common.CORRELATION_ID_HEADER
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.serialize
import org.slf4j.LoggerFactory

const val FORSTESIDE_PATH = "/api/foerstesidegenerator/v1/foersteside"

class ForstesideGeneratorHttpClient(
    private val forstesidegeneratorConfig: ApplicationConfig.ForstesideGeneratorConfig,
    private val azureAd: AzureAd,
) : ForstesideGeneratorClient {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun genererForsteside(
        request: PostForstesideRequest,
    ): Either<KunneIkkeGenerereForsteside, PostForstesideResponse> {
        val correlationId = getOrCreateCorrelationIdFromThreadLocal()
        val url = "${forstesidegeneratorConfig.url}$FORSTESIDE_PATH"

        val brukerToken = JwtToken.BrukerToken.fraCoroutineContextOrNull()

        val token = brukerToken
            ?.let { brukerToken ->
                azureAd.onBehalfOfToken(
                    originalToken = brukerToken.value,
                    otherAppId = forstesidegeneratorConfig.clientId,
                )
            }
            ?: azureAd.getSystemToken(
                otherAppId = forstesidegeneratorConfig.clientId,
            )

        val (_, response, result) = url.httpPost()
            .authentication().bearer(token)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header(CORRELATION_ID_HEADER, correlationId)
            .body(serialize(request))
            .responseString()

        return result.fold(
            { deserialize<PostForstesideResponse>(it).right() },
            {
                log.error(
                    "Kall mot ForstesideGeneratorHttpClient feilet med status ${response.statusCode}",
                    it,
                )
                KunneIkkeGenerereForsteside.left()
            },
        )
    }
}
