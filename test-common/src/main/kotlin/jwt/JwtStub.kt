package no.nav.su.se.bakover.test.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.auth.AuthStubCommonConfig
import no.nav.su.se.bakover.common.infrastructure.config.AzureConfig
import no.nav.su.se.bakover.test.applicationConfig
import org.jetbrains.annotations.TestOnly
import java.util.Date

val jwtStub get() = JwtStub(applicationConfig().azure)

const val DEFAULT_IDENT = "Z990Lokal"

class JwtStub(
    private val azureConfig: AzureConfig,
) {
    @TestOnly
    fun createJwtToken(
        subject: String = "serviceUserTestUsername",
        roller: List<Brukerrolle> = listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant, Brukerrolle.Veileder),
        navIdent: String? = DEFAULT_IDENT,
        navn: String? = "Brukerens navn",
        audience: String = azureConfig.clientId,
        expiresAt: Date = Date(Long.MAX_VALUE),
        issuer: String = AuthStubCommonConfig.ISSUER,
        externalRoles: List<String>? = null,
    ): String {
        val tokenBuilder = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("NAVident", navIdent)
            .withClaim("name", navn)
            .withKeyId(AuthStubCommonConfig.KEY_ID)
            .withSubject(subject)
            .withArrayClaim("groups", roller.map(::toAzureTestGroup).toTypedArray())
            .withClaim("oid", subject + "oid")
            .withExpiresAt(expiresAt)

        if (externalRoles != null) {
            tokenBuilder.withArrayClaim("roles", externalRoles.toTypedArray())
        }

        return tokenBuilder.sign(Algorithm.RSA256(AuthStubCommonConfig.public, AuthStubCommonConfig.private))
    }

    @TestOnly
    fun createCustomJwtToken(
        subject: String? = null,
        roller: List<Brukerrolle>? = null,
        navIdent: String? = null,
        navn: String? = null,
        audience: String? = null,
        expiresAt: Date = Date(Long.MAX_VALUE),
        issuer: String = AuthStubCommonConfig.ISSUER,
        externalRoles: List<String>? = null,
    ): String {
        val tokenBuilder = JWT.create()
            .withKeyId(AuthStubCommonConfig.KEY_ID)
            .withExpiresAt(expiresAt)

        subject?.let { tokenBuilder.withSubject(it) }
        issuer.let { tokenBuilder.withIssuer(it) }
        audience?.let { tokenBuilder.withAudience(it) }
        navIdent?.let { tokenBuilder.withClaim("NAVident", it) }
        navn?.let { tokenBuilder.withClaim("name", it) }
        roller?.let { tokenBuilder.withArrayClaim("groups", it.map(::toAzureTestGroup).toTypedArray()) }
        subject?.let { tokenBuilder.withClaim("oid", it + "oid") }

        externalRoles?.let { tokenBuilder.withArrayClaim("roles", it.toTypedArray()) }

        return tokenBuilder.sign(Algorithm.RSA256(AuthStubCommonConfig.public, AuthStubCommonConfig.private))
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
