package no.nav.su.se.bakover.web.stubs

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import no.nav.su.se.bakover.common.infrastructure.auth.AuthStubCommonConfig
import java.util.Base64

internal data object JwkProviderStub : JwkProvider {
    override fun get(keyId: String?) = Jwk(
        AuthStubCommonConfig.keyId,
        "RSA",
        "RS256",
        null,
        emptyList(),
        null,
        null,
        null,
        mapOf(
            "e" to Base64.getUrlEncoder().encodeToString(AuthStubCommonConfig.public.publicExponent.toByteArray()),
            "n" to Base64.getUrlEncoder().encodeToString(AuthStubCommonConfig.public.modulus.toByteArray()),
        ),
    )
}
