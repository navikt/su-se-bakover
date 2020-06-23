package no.nav.su.se.bakover.client

interface ClientsBuilder {
    fun build(): Clients
}

data class Clients(
        val jwk: Jwk,
        val oauth: OAuth,
        val personOppslag: PersonOppslag,
        val inntektOppslag: InntektOppslag
)

object ClientBuilder : ClientsBuilder {
    private val env = System.getenv()
    internal fun azure(
            clientId: String = env.getOrDefault("AZURE_CLIENT_ID", "24ea4acb-547e-45de-a6d3-474bd8bed46e"),
            clientSecret: String = env.getOrDefault("AZURE_CLIENT_SECRET", "secret"),
            tokenEndpoint: String
    ): OAuth {
        return AzureClient(clientId, clientSecret, tokenEndpoint)
    }

    internal fun person(
            baseUrl: String = env.getOrDefault("SU_PERSON_URL", "http://su-person.default.svc.nais.local"),
            clientId: String = env.getOrDefault("SU_PERSON_AZURE_CLIENT_ID", "76de0063-2696-423b-84a4-19d886c116ca"),
            oAuth: OAuth
    ): PersonOppslag {
        return SuPersonClient(baseUrl, clientId, oAuth)
    }

    internal fun inntekt(
            baseUrl: String = env.getOrDefault("SU_INNTEKT_URL", "http://su-inntekt.default.svc.nais.local"),
            clientId: String = env.getOrDefault("SU_INNTEKT_AZURE_CLIENT_ID", "9cd61904-33ad-40e8-9cc8-19e4dab588c5"),
            oAuth: OAuth,
            personOppslag: PersonOppslag
    ): InntektOppslag {
        return SuInntektClient(baseUrl, clientId, oAuth, personOppslag)
    }

    internal fun jwk(wellKnownUrl: String = env.getOrDefault("AZURE_WELLKNOWN_URL", "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0/.well-known/openid-configuration")): Jwk {
        return JwkClient(wellKnownUrl)
    }

    override fun build(): Clients {
        val jwk = jwk()
        val azure = azure(tokenEndpoint = jwk.config().getString("token_endpoint"))
        val personOppslag = person(oAuth = azure)
        val inntektOppslag = inntekt(oAuth = azure, personOppslag = personOppslag)
        return Clients(jwk, azure, personOppslag, inntektOppslag)
    }
}