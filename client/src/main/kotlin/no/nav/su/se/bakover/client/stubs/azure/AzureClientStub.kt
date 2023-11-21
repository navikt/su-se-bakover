package no.nav.su.se.bakover.client.stubs.azure

import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.infrastructure.auth.AuthStubCommonConfig

data object AzureClientStub : AzureAd {
    override fun onBehalfOfToken(originalToken: String, otherAppId: String) = originalToken

    override val jwksUri = "http://localhost:8080/jwks"
    override fun getSystemToken(otherAppId: String) = "token"

    override val issuer = AuthStubCommonConfig.ISSUER
}
