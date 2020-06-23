package no.nav.su.se.bakover.web

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import no.nav.su.se.bakover.AZURE_CLIENT_ID
import no.nav.su.se.bakover.AZURE_ISSUER
import no.nav.su.se.bakover.AZURE_REQUIRED_GROUP
import no.nav.su.se.bakover.SUBJECT
import no.nav.su.se.bakover.client.AzureStub
import no.nav.su.se.bakover.client.RSAKeyPairGenerator
import java.time.Instant
import java.util.*

object Jwt {
    val keys = RSAKeyPairGenerator.generate()
    fun create(
            subject: String = SUBJECT,
            groups: List<String> = listOf(AZURE_REQUIRED_GROUP),
            audience: String = AZURE_CLIENT_ID,
            expiresAt: Date = Date.from(Instant.now().plusSeconds(3600)),
            algorithm: Algorithm = RSAKeyPairGenerator.generate().let { Algorithm.RSA256(keys.first, keys.second) }
    ): String {
        return "Bearer ${JWT.create()
                .withIssuer(AZURE_ISSUER)
                .withAudience(audience)
                .withKeyId("key-1234")
                .withSubject(subject)
                .withArrayClaim("groups", groups.toTypedArray())
                .withClaim("oid", UUID.randomUUID().toString())
                .withExpiresAt(expiresAt)
                .sign(algorithm)}"
    }

    fun create(azureStub: AzureStub) = create(algorithm = Algorithm.RSA256(azureStub.keys().first, azureStub.keys().second))
}