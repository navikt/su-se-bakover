package no.nav.su.se.bakover.web

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.*

object Jwt {
    val keys = generate()
    fun create(
        subject: String = SUBJECT,
        groups: List<String> = listOf(AZURE_REQUIRED_GROUP),
        audience: String = AZURE_CLIENT_ID,
        expiresAt: Date = Date.from(Instant.now().plusSeconds(3600)),
        algorithm: Algorithm = Algorithm.RSA256(keys.first, keys.second)
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

    fun generate(): Pair<RSAPublicKey, RSAPrivateKey> {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(512)
        val keypair = keyPairGenerator.genKeyPair()
        return keypair.public as RSAPublicKey to keypair.private as RSAPrivateKey
    }
}
