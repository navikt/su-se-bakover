package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
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

    override fun hentSamletSkattegrunnlag(
        accessToken: AccessToken,
        fnr: Fnr,
    ): Either<SkatteoppslagFeil, SamletSkattegrunnlag> {
        val getRequest = HttpRequest.newBuilder()
            // TODO: Ikke hardkode år
            .uri(URI.create("${skatteetatenConfig.apiUri}/api/formueinntekt/summertskattegrunnlag/nav/2021/$fnr"))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${accessToken.value}")
            .GET()
            .build()

        Either.catch {
            client.send(getRequest, HttpResponse.BodyHandlers.ofString()).let { response ->
                if (!response.isSuccess()) {
                    log.warn("Kall mot skatteetatens api feilet med statuskode ${response.statusCode()}")
                    sikkerLogg.warn(
                        "Kall mot skatteetatens api feilet med statuskode ${response.statusCode()} og følgende feil: ${response.body()}. " +
                            "Request $getRequest er forespørselen mot skatteetaten som feilet.",
                    )

                    objectMapper.readValue(response.body(), SkattedataFeilrespons::class.java).let {
                        log.warn(
                            """
                            Mappet feilmelding fra skatteetaten.
                            Feilkode: ${it.kode.name}
                            Http kode: ${it.kode.httpKode}
                            Beskrivelse: ${it.kode.beskrivelse}
                            Korrelasjonsid: ${it.korrelasjonsid}
                            """.trimIndent(),
                        )
                        val mappedFeil = when (it.kode) {
                            SkattedataFeilrespons.Feilkode.`SSG-007` -> SkatteoppslagFeil.FantIkkePerson
                            SkattedataFeilrespons.Feilkode.`SSG-008` -> SkatteoppslagFeil.FantIkkeSkattegrunnlagForGittÅr
                            SkattedataFeilrespons.Feilkode.`SSG-010` -> SkatteoppslagFeil.SkattegrunnlagFinnesIkkeLenger
                            else -> SkatteoppslagFeil.Apifeil
                        }

                        return mappedFeil.left()
                    }
                } else {
                    return objectMapper.readValue(response.body(), SamletSkattegrunnlag::class.java).right()
                }
            }
        }.getOrHandle {
            if (it is JsonMappingException || it is JsonProcessingException) {
                log.error("Feilet under deserializering i henting av data fra skatteetaten. Melding: ${it.message}")
                sikkerLogg.error("Feilet under deserializering i henting av data fra skatteetaten.", it)
            } else {
                log.warn("Fikk en exception ${it.message} i henting av data fra skatteetaten.", it)
                sikkerLogg.warn(
                    "Fikk en exception ${it.message} i henting av data fra skatteetaten. " +
                        "Request $getRequest er forespørselen mot skatteetaten som feilet.",
                )
            }

            return SkatteoppslagFeil.Nettverksfeil(it).left()
        }
    }
}

private data class SkattedataFeilrespons(val kode: Feilkode, val melding: String, val korrelasjonsid: String) {
    /*
    * Docs: https://skatteetaten.github.io/datasamarbeid-api-dokumentasjon/reference_summertskattegrunnlag.html
    */
    enum class Feilkode(val httpKode: Int, val beskrivelse: String) {
        `SSG-001`(500, "Uventet feil på tjenesten"),
        `SSG-002`(500, "Uventet feil i et bakenforliggende system"),
        `SSG-003`(404, "Ukjent url benyttet"),
        `SSG-004`(401, "Feil i forbindelse med autentisering"),
        `SSG-005`(403, "Feil i forbindelse med autorisering"),
        `SSG-006`(400, "Feil i forbindelse med validering av inputdata"),
        `SSG-007`(404, "Ikke treff på oppgitt personidentifikator"),
        `SSG-008`(404, "Ingen summert skattegrunnlag funnet på oppgitt personidentifikator og inntektsår"),
        `SSG-009`(406, "Feil tilknyttet dataformat. Kun json eller xml er støttet"),
        `SSG-010`(410, "Skattegrunnlag finnes ikke lenger"),
    }
}
