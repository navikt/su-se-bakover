package no.nav.su.se.bakover.client

import no.nav.su.person.sts.StsClient
import no.nav.su.person.sts.TokenOppslag
import no.nav.su.se.bakover.client.stubs.InntektOppslagStub
import no.nav.su.se.bakover.client.stubs.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.TokenOppslagStub
import org.slf4j.LoggerFactory

interface HttpClientsBuilder {
    fun build(
        azure: OAuth = HttpClientBuilder.azure(),
        personOppslag: PersonOppslag = HttpClientBuilder.person(oAuth = azure),
        inntektOppslag: InntektOppslag = HttpClientBuilder.inntekt(oAuth = azure, personOppslag = personOppslag),
        tokenOppslag: TokenOppslag = HttpClientBuilder.token()
    ): HttpClients
}

data class HttpClients(
    val oauth: OAuth,
    val personOppslag: PersonOppslag,
    val inntektOppslag: InntektOppslag,
    val tokenOppslag: TokenOppslag
)

object HttpClientBuilder : HttpClientsBuilder {
    private val env = System.getenv()
    internal fun azure(
        clientId: String = env.getOrDefault("AZURE_CLIENT_ID", "24ea4acb-547e-45de-a6d3-474bd8bed46e"),
        clientSecret: String = env.getOrDefault("AZURE_CLIENT_SECRET", "secret"),
        wellknownUrl: String = env.getOrDefault(
            "AZURE_WELLKNOWN_URL",
            "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0/.well-known/openid-configuration"
        )
    ): OAuth {
        return AzureClient(clientId, clientSecret, wellknownUrl)
    }

    internal fun person(
        baseUrl: String = env.getOrDefault("SU_PERSON_URL", "http://su-person.default.svc.nais.local"),
        clientId: String = env.getOrDefault("SU_PERSON_AZURE_CLIENT_ID", "76de0063-2696-423b-84a4-19d886c116ca"),
        oAuth: OAuth
    ): PersonOppslag = when (env.isLocalOrRunningTests()) {
        true -> PersonOppslagStub.also { logger.warn("********** Using stub for ${PersonOppslag::class.java} **********") }
        else -> SuPersonClient(baseUrl, clientId, oAuth)
    }

    internal fun inntekt(
        baseUrl: String = env.getOrDefault("SU_INNTEKT_URL", "http://su-inntekt.default.svc.nais.local"),
        clientId: String = env.getOrDefault("SU_INNTEKT_AZURE_CLIENT_ID", "9cd61904-33ad-40e8-9cc8-19e4dab588c5"),
        oAuth: OAuth,
        personOppslag: PersonOppslag
    ): InntektOppslag = when (env.isLocalOrRunningTests()) {
        true -> InntektOppslagStub.also { logger.warn("********** Using stub for ${InntektOppslag::class.java} **********") }
        else -> SuInntektClient(baseUrl, clientId, oAuth, personOppslag)
    }

    internal fun token(
        baseUrl: String = env.getOrDefault("STS_URL", "http://security-token-service.default.svc.nais.local"),
        username: String = env.getOrDefault("username", "username"),
        password: String = env.getOrDefault("password", "password")
    ): TokenOppslag = when (env.isLocalOrRunningTests()) {
        true -> TokenOppslagStub.also { logger.warn("********** Using stub for ${TokenOppslag::class.java} **********") }
        else -> StsClient(baseUrl, username, password)
    }

    override fun build(
        azure: OAuth,
        personOppslag: PersonOppslag,
        inntektOppslag: InntektOppslag,
        tokenOppslag: TokenOppslag
    ): HttpClients {
        return HttpClients(azure, personOppslag, inntektOppslag, tokenOppslag)
    }

    private val logger = LoggerFactory.getLogger(HttpClientBuilder::class.java)
}
