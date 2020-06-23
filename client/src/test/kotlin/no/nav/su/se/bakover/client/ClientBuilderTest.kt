package no.nav.su.se.bakover.client

class ClientBuilderTest(
        private val baseUrl: String
) : ClientsBuilder {

    override fun build(): Clients {
        val jwk = ClientBuilder.jwk(wellKnownUrl = "$baseUrl$AZURE_WELL_KNOWN_URL")
        val azure = ClientBuilder.azure(tokenEndpoint = jwk.config().getString("token_endpoint"))
        val personOppslag = ClientBuilder.person(baseUrl = baseUrl, oAuth = azure)
        val inntektOppslag = ClientBuilder.inntekt(baseUrl = baseUrl, oAuth = azure, personOppslag = personOppslag)
        return Clients(jwk, azure, personOppslag, inntektOppslag)
    }
}