package no.nav.su.se.bakover.client

object ClientBuilder {
    fun azure(env: Map<String, String>): OAuth {
        return AzureClient(
                env.getOrDefault("azure.clientId", ""),
                env.getOrDefault("azure.clientSecret", ""),
                env.getOrDefault("token_endpoint", "")
        )
    }

    fun person(env: Map<String, String>, oAuth: OAuth): PersonOppslag {
        return SuPersonClient(
                env.getOrDefault("integrations.suPerson.url", ""),
                env.getOrDefault("integrations.suPerson.clientId", ""),
                oAuth
        )
    }

    fun inntekt(env: Map<String, String>, oAuth: OAuth, personOppslag: PersonOppslag): InntektOppslag {
        return SuInntektClient(
                env.getOrDefault("integrations.suInntekt.url", ""),
                env.getOrDefault("integrations.suInntekt.clientId", ""),
                oAuth,
                personOppslag
        )
    }
}