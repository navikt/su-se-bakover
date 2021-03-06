package no.nav.su.se.bakover.web.stubs

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.stubs.AuthStubCommonConfig
import no.nav.su.se.bakover.domain.Brukerrolle
import org.jetbrains.annotations.TestOnly
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

internal class JwtStub(
    private val applicationConfig: ApplicationConfig
) {
    @TestOnly
    fun createJwtToken(
        subject: String = "enSaksbehandler",
        roller: List<Brukerrolle> = listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant, Brukerrolle.Veileder),
        navIdent: String? = "Z990Lokal",
        navn: String? = "Brukerens navn",
        audience: String = applicationConfig.azure.clientId,
        expiresAt: Date = Date.from(Instant.now().plus(1L, ChronoUnit.DAYS)),
        issuer: String = AuthStubCommonConfig.issuer
    ): String {
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("NAVident", navIdent)
            .withClaim("name", navn)
            .withKeyId(AuthStubCommonConfig.keyId)
            .withSubject(subject)
            .withArrayClaim("groups", roller.map(::toAzureTestGroup).toTypedArray())
            .withClaim("oid", subject + "oid")
            .withExpiresAt(expiresAt)
            .sign(Algorithm.RSA256(AuthStubCommonConfig.public, AuthStubCommonConfig.private))
            .toString()
    }

    private fun toAzureTestGroup(rolle: Brukerrolle) =
        when (rolle) {
            Brukerrolle.Attestant -> applicationConfig.azure.groups.attestant
            Brukerrolle.Saksbehandler -> applicationConfig.azure.groups.saksbehandler
            Brukerrolle.Veileder -> applicationConfig.azure.groups.veileder
            Brukerrolle.Drift -> applicationConfig.azure.groups.drift
        }
}
@TestOnly
fun String.asBearerToken(): String {
    return "Bearer $this"
}
