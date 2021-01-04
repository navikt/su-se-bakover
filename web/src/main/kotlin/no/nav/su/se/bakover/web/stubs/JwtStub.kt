package no.nav.su.se.bakover.web.stubs

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.domain.Brukerrolle
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

object JwtStub { // TODO connection between JWT and JWK values
    val keys = generate()
    fun create(
        subject: String = "enSaksbehandler",
        roller: List<Brukerrolle> = listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant, Brukerrolle.Veileder),
        audience: String = Config.azureClientId,
        expiresAt: Date = Date.from(Instant.now().plus(1L, ChronoUnit.DAYS)),
        algorithm: Algorithm = Algorithm.RSA256(keys.first, keys.second)
    ): String {
        return "Bearer ${
            JWT.create()
                .withIssuer("localhost")
                .withAudience(audience)
                .withKeyId("key-1234")
                .withSubject(subject)
                .withArrayClaim("groups", roller.map(Brukerrolle::toAzureGroup).toTypedArray())
                .withClaim("oid", subject + "oid")
                .withExpiresAt(expiresAt)
                .sign(algorithm)
        }"
    }

    fun generate(): Pair<RSAPublicKey, RSAPrivateKey> {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(512)
        val keypair = keyPairGenerator.genKeyPair()
        return keypair.public as RSAPublicKey to keypair.private as RSAPrivateKey
    }
}
