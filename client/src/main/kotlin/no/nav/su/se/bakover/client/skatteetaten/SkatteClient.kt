package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.left
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import no.nav.su.se.bakover.client.isSuccess
import no.nav.su.se.bakover.common.MaskinportenConfig
import no.nav.su.se.bakover.common.SkatteetatenConfig
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.domain.Fnr
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class SkatteClient(private val maskinportenConfig: MaskinportenConfig, private val skatteetatenConfig: SkatteetatenConfig) : SkatteOppslag {

    private var token: SkatteToken? = null

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private fun refreshSession() {
        if (SkatteToken.isValid(token)) {
            return
        }

        val key = RSAKey.parse(maskinportenConfig.clientJwk)

        val signedJWT = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.keyID)
                .type(JOSEObjectType.JWT)
                .build(),
            JWTClaimsSet.Builder()
                .audience(maskinportenConfig.issuer)
                .issuer(maskinportenConfig.clientId) // Vi signerer denne JWT
                .claim("scope", maskinportenConfig.scopes)
                .issueTime(Date.from(Tidspunkt.now().instant))
                .expirationTime(Date.from(Tidspunkt.now().instant.plusSeconds(5 * 60)))
                .build()
        )
        signedJWT.sign(RSASSASigner(key.toRSAPrivateKey()))
        val body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${signedJWT.serialize()}"
        val postRequest = HttpRequest.newBuilder()
            .uri(URI.create("maskinporten token path fra env"))
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        client.send(postRequest, HttpResponse.BodyHandlers.ofString()).let { response ->
            if (!response.isSuccess()) {
                log.error("Feil i henting av token mot maskinporten, ${response.statusCode()}")
            } else {
                log.info("Vi fikk hentet token! wow. ${response.body()}")
                token = SkatteToken(json = JSONObject(response.body()))
            }
        }
    }

    override fun hentSkattemelding(fnr: Fnr): Either<Unit, Skattemelding> {

        return Unit.left()
    }

    private data class SkatteToken(
        private val json: JSONObject
    ) {

        val accessToken: String = json.getString("access_token")
        private val expiresIn: Int = json.getInt("expires_in")
        private val expirationTime = LocalDateTime.now().plusSeconds(expiresIn - 20L)

        companion object {
            fun isValid(token: SkatteToken?): Boolean {
                return when (token) {
                    null -> false
                    else -> !isExpired(token)
                }
            }

            private fun isExpired(token: SkatteToken) = token.expirationTime.isBefore(LocalDateTime.now())
        }
    }
}
