package no.nav.su.se.bakover.client.maskinporten

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import no.nav.su.se.bakover.client.ExpiringTokenResponse
import no.nav.su.se.bakover.client.isSuccess
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Date

class MaskinportenHTTPClient(private val maskinportenConfig: ApplicationConfig.ClientsConfig.MaskinportenConfig) : MaskinportenClient {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private fun lagJWTGrant(): JWT {
        val key = RSAKey.parse(maskinportenConfig.clientJwk)

        return SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.keyID)
                .type(JOSEObjectType.JWT)
                .build(),
            JWTClaimsSet.Builder()
                .audience(maskinportenConfig.issuer)
                .issuer(maskinportenConfig.clientId) // Vi signerer denne JWT
                .claim("scope", maskinportenConfig.scopes)
                .issueTime(Date.from(Tidspunkt.now().instant))
                .expirationTime(Date.from(Tidspunkt.now().instant.plusSeconds(60)))
                .build()
        ).apply {
            sign(RSASSASigner(key.toRSAPrivateKey()))
        }
    }

    override fun hentNyToken(): Either<KunneIkkeHenteToken, ExpiringTokenResponse> {
        val signedJWT = lagJWTGrant()
        val body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${signedJWT.serialize()}"

        val postRequest = HttpRequest.newBuilder()
            .uri(URI.create(maskinportenConfig.tokenEndpoint))
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        log.info("request-body: $body")
        Either.catch {
            client.send(postRequest, HttpResponse.BodyHandlers.ofString()).let { response ->
                if (!response.isSuccess()) {
                    log.error("Feil i henting av token mot maskinporten, ${response.statusCode()}")
                    log.info("response body: ${response.body()}") // fjern dette sen
                    return KunneIkkeHenteToken.UgyldigRespons(response.statusCode(), response.body()).left()
                } else {
                    log.info("Vi fikk hentet token! wow. ${response.body()}")
                    return ExpiringTokenResponse(JSONObject(response.body())).right()
                }
            }
        }.getOrHandle { exception -> return KunneIkkeHenteToken.Nettverksfeil(exception).left() }
    }
}

sealed class KunneIkkeHenteToken {
    data class Nettverksfeil(val feil: Throwable) : KunneIkkeHenteToken()
    data class UgyldigRespons(val httpStatus: Int, val body: String) : KunneIkkeHenteToken()
}
