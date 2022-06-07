package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.client.isSuccess
import no.nav.su.se.bakover.common.ApplicationConfig.ClientsConfig.SkatteetatenConfig
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.SamletSkattegrunnlag
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class SkatteClient(private val skatteetatenConfig: SkatteetatenConfig) : Skatteoppslag {

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun hentSamletSkattegrunnlag(accessToken: AccessToken, fnr: Fnr): Either<SkatteoppslagFeil, SamletSkattegrunnlag> {
        val getRequest = try {
            HttpRequest.newBuilder()
                // TODO: Ikke hardkode år
                .uri(URI.create("${skatteetatenConfig.apiUri}/nav/2021/$fnr"))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer ${accessToken.token}")
                .GET()
                .build()
        } catch (e: Exception) {
            log.warn("Vi klarte ikke å bygge en request en gang. Dette betyr at vi ikke henter noe data fra skatteetaten.", e)
            throw e
        }

        Either.catch {
            client.send(getRequest, HttpResponse.BodyHandlers.ofString()).let { response ->
                if (!response.isSuccess()) {
                    log.debug("Kall mot skatteetatens api feilet med statuskode ${response.statusCode()} og følgende feil: ${response.body()}")
                    sikkerLogg.warn(
                        "Kall mot skatteetatens api feilet med statuskode ${response.statusCode()} og følgende feil: ${response.body()}. " +
                            "Request $getRequest er forespørselen mot skatteetaten som feilet."
                    )
                    return SkatteoppslagFeil.KunneIkkeHenteSkattedata(
                        statusCode = response.statusCode(),
                        feilmelding = "Kall mot skatteetatens api feilet",
                    ).left()
                } else {
                    log.info("Vi fikk hentet token! wow. ${response.body()}")
                    return objectMapper.readValue(response.body(), SamletSkattegrunnlag::class.java).right()
                }
            }
        }.getOrHandle {
            log.warn("Fikk en exception ${it.message} i henting av data fra skatteetaten.", it)
            sikkerLogg.warn(
                "Fikk en exception ${it.message} i henting av data fra skatteetaten. " +
                    "Request $getRequest er forespørselen mot skatteetaten som feilet."
            )
            return SkatteoppslagFeil.Nettverksfeil(it).left()
        }
    }
}
