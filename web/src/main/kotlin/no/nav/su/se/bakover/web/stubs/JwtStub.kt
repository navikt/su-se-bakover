package no.nav.su.se.bakover.web.stubs

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.domain.Brukerrolle
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

/*
object JwtStubz {
    lateinit var jwtStub: JwtStub
    lateinit var applicationConfig: ApplicationConfig

    fun configure(applicationConfig: ApplicationConfig) {
        this.applicationConfig = applicationConfig
        this.jwtStub = JwtStub(applicationConfig)
    }

    fun create(
        subject: String = "enSaksbehandler",
        roller: List<Brukerrolle> = listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant, Brukerrolle.Veileder),
        audience: String = applicationConfig.azure.clientId,
        expiresAt: Date = Date.from(Instant.now().plus(1L, ChronoUnit.DAYS)),
    ): String = jwtStub.create(subject, roller, audience, expiresAt)
}
*/
class JwtStub(
    private val applicationConfig: ApplicationConfig
) { // TODO connection between JWT and JWK values
    fun create(
        subject: String = "enSaksbehandler",
        roller: List<Brukerrolle> = listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant, Brukerrolle.Veileder),
        audience: String = applicationConfig.azure.clientId,
        expiresAt: Date = Date.from(Instant.now().plus(1L, ChronoUnit.DAYS)),
    ): String {
        return "Bearer ${
        JWT.create()
            .withIssuer("localhost")
            .withAudience(audience)
            .withKeyId("key-1234")
            .withSubject(subject)
            .withArrayClaim("groups", roller.map(::toAzureTestGroup).toTypedArray())
            .withClaim("oid", subject + "oid")
            .withExpiresAt(expiresAt)
            .sign(Algorithm.RSA256(AuthStubSiginingKeys.public, AuthStubSiginingKeys.private))
        }"
    }

    private fun toAzureTestGroup(rolle: Brukerrolle) =
        when (rolle) {
            Brukerrolle.Attestant -> applicationConfig.azure.groups.attestant
            Brukerrolle.Saksbehandler -> applicationConfig.azure.groups.saksbehandler
            Brukerrolle.Veileder -> applicationConfig.azure.groups.veileder
        }
}
