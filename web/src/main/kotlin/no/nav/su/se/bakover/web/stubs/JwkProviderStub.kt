package no.nav.su.se.bakover.web.stubs

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import java.util.Base64

internal object JwkProviderStub : JwkProvider {
    override fun get(keyId: String?) = Jwk(
        "key-1234",
        "RSA",
        "RS256",
        null,
        emptyList(),
        null,
        null,
        null,
        mapOf(
            "e" to String(Base64.getEncoder().encode(JwtStub.keys.first.publicExponent.toByteArray())),
            "n" to String(Base64.getEncoder().encode(JwtStub.keys.first.modulus.toByteArray()))
        )
    )
}
