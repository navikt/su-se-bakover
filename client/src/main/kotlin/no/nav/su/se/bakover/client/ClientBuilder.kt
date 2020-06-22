package no.nav.su.se.bakover.client

object ClientBuilder {
    fun azure(env: Map<String, String>): OAuth {
        return AzureClient(
                env.getOrDefault("azure.clientId", ""),
                env.getOrDefault("azure.clientSecret", ""),
                env.getOrDefault("token_endpoint", "")
        )
    }
}