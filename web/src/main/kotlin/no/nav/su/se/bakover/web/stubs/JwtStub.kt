package no.nav.su.se.bakover.web.stubs

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.stubs.AuthStubCommonConfig
import no.nav.su.se.bakover.domain.Brukerrolle
import org.jetbrains.annotations.TestOnly
import java.util.Date

class JwtStub(
    private val azureConfig: ApplicationConfig.AzureConfig,
) {
    @TestOnly
    fun createJwtToken(
        subject: String = "enSaksbehandler",
        roller: List<Brukerrolle> = listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant, Brukerrolle.Veileder),
        navIdent: String? = "Z990Lokal",
        navn: String? = "Brukerens navn",
        audience: String = azureConfig.clientId,
        expiresAt: Date = Date(Long.MAX_VALUE),
        issuer: String = AuthStubCommonConfig.issuer,
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
            Brukerrolle.Attestant -> azureConfig.groups.attestant
            Brukerrolle.Saksbehandler -> azureConfig.groups.saksbehandler
            Brukerrolle.Veileder -> azureConfig.groups.veileder
            Brukerrolle.Drift -> azureConfig.groups.drift
        }
}
@TestOnly
fun String.asBearerToken(): String {
    return "Bearer $this"
}
