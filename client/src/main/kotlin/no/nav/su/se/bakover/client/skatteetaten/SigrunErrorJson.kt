package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.merge
import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.LoggerFactory
import vilkår.skatt.domain.Stadie
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.time.Year

private data class SigrunErrorJson(
    /** Eksempeldata: 2023-03-28T10:48:32.600+0200 */
    val timestamp: String? = null,
    /** Eksempeldata: 404 */
    val status: Int? = null,
    /** Eksempeldata: Not Found */
    val error: String? = null,
    /** Eksempeldata: SKE */
    val source: String? = null,
    /** Eksempeldata: Fant ingen grunnag med gitt personidentifikator og kriterier hos skatt. Korrelasjonsid: 5d79e63c-6bd0-7e59-aa63-287c8812dd14. Spurt på år 2022 og tjeneste SPESIFISERT_SUMMERT_SKATTEGRUNNLAG */
    val message: String? = null,
    /** Eksempeldata: /api/spesifisertsummertskattegrunnlag */
    val path: String? = null,
    /** Eksempeldata: {"kode":"SSG-007","melding":"Fant ikke noen person for gitt personidentifikator.","korrelasjonsid":"5d79e63c-6bd0-7e59-aa63-287c8812dd14"} */
    @JsonAlias("ske-message")
    val skeMessage: SkeMessage? = null,
) {
    /**
     * Fra dokumentasjonen (https://skatteetaten.github.io/datasamarbeid-api-dokumentasjon/reference_spesifisertsummertskattegrunnlag.html):
     * SSG-001 	500 	Uventet feil på tjenesten.
     * SSG-002 	500 	Uventet feil i et bakenforliggende system.
     * SSG-003 	404 	Ukjent url benyttet.
     * SSG-004 	401 	Feil i forbindelse med autentisering.
     * BSA-005 	403 	Feil i forbindelse med autorisering.
     * SSG-006 	400 	Feil i forbindelse med validering av inputdata.
     * SSG-007 	404 	Ikke treff på oppgitt personidentifikator.
     * SSG-008 	404 	Ingen spesifisert summert skattegrunnlag funnet for oppgitt identifikator og inntektsår.
     * SSG-009 	406 	Feil tilknyttet dataformat. Kun json eller xml er støttet.
     * SSG-010 	403 	Feil i forbindelse med samtykketoken.
     * SSG-011 	410 	Skattegrunnlag finnes ikke lenger.
     */
    data class SkeMessage(
        /** Eksempeldata: SSG-007 */
        val kode: String? = null,
        /** Eksempeldata: Fant ikke noen person for gitt personidentifikator. */
        val melding: String? = null,
        /** Eksempeldata: 5d79e63c-6bd0-7e59-aa63-287c8812dd14 */
        val korrelasjonsid: String? = null,
    ) {
        fun tilSkatteoppslagFeil(år: Year): SkatteoppslagFeil {
            return when (kode) {
                // Feil i forbindelse med autentisering. Feil i forbindelse med autorisering. Feil i forbindelse med samtykketoken.
                "SSG-004", "BSA-005", "SSG-010" -> SkatteoppslagFeil.ManglerRettigheter

                // Feil i forbindelse med validering av inputdata.
                // Kan oppstå dersom vi sender inn et inntektsår de ikke støtter enda. Oppdaget 2023-01-01, da vi sendte inn 2023. Kan også skje dersom vi sender inn et for tidlig år.
                "SSG-006" -> if (melding?.contains("inntektsår") == true) {
                    SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år)
                } else {
                    SkatteoppslagFeil.OppslagetInneholderUgyldigData
                }
                // Ikke treff på oppgitt personidentifikator. Ingen spesifisert summert skattegrunnlag funnet for oppgitt identifikator og inntektsår.
                "SSG-007", "SSG-008" -> SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år)
                // TODO jah: Vi kommer nok til å støte på SSG-011 (Skattegrunnlag finnes ikke lenger.) på et tidspunkt. Vi bør sannsynligvis formidle det til saksbehandler. Må gjøres manuelt vha. loggene i dag.
                else -> SkatteoppslagFeil.UkjentFeil(RuntimeException("Uforventet feilmelding fra Sigrun. Se sikkerlogg for detaljer."))
            }
        }
    }
}

private fun String.tilSkatteoppslagFeil(inntektsår: Year): SkatteoppslagFeil {
    return Either.catch {
        deserialize<SigrunErrorJson>(this).skeMessage?.tilSkatteoppslagFeil(inntektsår)
            ?: SkatteoppslagFeil.UkjentFeil(
                RuntimeException("Ukjent feilmeldingsformat fra Sigrun; mangler ske-message. Se sikkerlogg for detaljer."),
            )
    }.mapLeft {
        SkatteoppslagFeil.UkjentFeil(it)
    }.merge()
}

internal fun håndterSigrunFeil(
    statusCode: Int,
    body: String,
    fnr: Fnr,
    inntektsår: Year,
    stadie: Stadie,
    getRequest: HttpRequest?,
): SkatteoppslagFeil {
    val requestHeaders: HttpHeaders? = getRequest?.headers()

    val log = LoggerFactory.getLogger("SigrunErrorJson.kt")

    fun logError(throwable: Throwable? = RuntimeException("Genererer en stacktrace.")) {
        log.error(
            "Kall mot Sigrun/skatteetatens api feilet med statuskode $statusCode. Se sikkerlogg for detaljer.",
            RuntimeException("Genererer stacktrace for enklere debugging."),
        )

        sikkerLogg.error(
            "Kall mot Sigrun/skatteetatens api feilet med statuskode $statusCode, Fnr: $fnr, Inntektsår: $inntektsår, Stadie: $stadie og følgende feil: $body. " +
                "Request $getRequest er forespørselen mot skatteetaten som feilet. headere $requestHeaders",
            throwable,
        )
    }

    return when (val feil = body.tilSkatteoppslagFeil(inntektsår)) {
        is SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr -> feil
        is SkatteoppslagFeil.Nettverksfeil -> {
            logError(feil.throwable)
            feil
        }

        is SkatteoppslagFeil.UkjentFeil -> {
            if (statusCode == 403) {
                SkatteoppslagFeil.ManglerRettigheter.also {
                    // Vi forventer ikke se denne, så vi logger som error inntil den blir plagsom.
                    logError()
                }
            }
            if (statusCode == 503) {
                SkatteoppslagFeil.Nettverksfeil(RuntimeException("Fikk 503 fra Sigrun. Prøv igjen senere."))
                    .also {
                        // Dersom denne kommer ofte, bør vi legge inn noe mer logikk. Melding til saksbehandler om at de kan prøve igjen? Retry? Bytte til warning?
                        logError(it.throwable)
                    }
            } else {
                logError(feil.throwable)
                feil
            }
        }

        is SkatteoppslagFeil.ManglerRettigheter,
        is SkatteoppslagFeil.OppslagetInneholderUgyldigData,
        -> {
            logError()
            feil
        }
    }
}
