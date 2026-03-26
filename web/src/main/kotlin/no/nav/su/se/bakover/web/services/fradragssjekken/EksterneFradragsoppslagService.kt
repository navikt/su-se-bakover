package no.nav.su.se.bakover.web.services.fradragssjekken

import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysPeriode
import no.nav.su.se.bakover.client.pesys.PesysPerioderForPerson
import no.nav.su.se.bakover.common.infrastructure.correlation.CORRELATION_ID_HEADER
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.MaksimumVedtakDto
import org.slf4j.Logger
import org.slf4j.MDC
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private const val AAP_PARALLELLE_OPPSLAG = 8
private val AAP_STONADSDAGER_PER_AR = BigDecimal(260)
private val AAP_MANEDER_PER_AR = BigDecimal(12)

internal class EksterneFradragsoppslagService(
    private val aapKlient: AapApiInternClient,
    private val pesysKlient: PesysClient,
    private val log: Logger,
) {
    fun hentPerioderForYtelser(
        sjekkplaner: List<SjekkPlan>,
        måned: Måned,
    ): EksterneOppslagsresultater {
        return EksterneOppslagsresultater(
            aap = hentAapOppslag(sjekkplaner.hentFnrForYtelse(EksternYtelse.AAP), måned),
            pesysAlder = hentPesysAlderOppslag(sjekkplaner.hentFnrForYtelse(EksternYtelse.PESYS_ALDER), måned.fraOgMed),
            pesysUføre = hentPesysUføreOppslag(sjekkplaner.hentFnrForYtelse(EksternYtelse.PESYS_UFORE), måned.fraOgMed),
        )
    }

    private fun hentPesysAlderOppslag(
        fnr: List<Fnr>,
        dato: LocalDate,
    ): Map<Fnr, EksterntOppslag> {
        return pesysKlient.hentVedtakForPersonPaaDatoAlder(fnr, dato).fold(
            ifLeft = {
                log.warn("Fradragssjekk: Eksternt kall mot {} feilet for {} personer", EksternYtelse.PESYS_ALDER, fnr.size)
                lagFeilResultat(fnr, "Eksternt kall mot ${EksternYtelse.PESYS_ALDER} feilet")
            },
            ifRight = {
                mapPesysOppslag(fnr = fnr, dato = dato, perioderForPerson = it.resultat)
            },
        )
    }

    private fun hentPesysUføreOppslag(
        fnr: List<Fnr>,
        dato: LocalDate,
    ): Map<Fnr, EksterntOppslag> {
        return pesysKlient.hentVedtakForPersonPaaDatoUføre(fnr, dato).fold(
            ifLeft = {
                log.warn("Fradragssjekk: Eksternt kall mot {} feilet for {} personer", EksternYtelse.PESYS_UFORE, fnr.size)
                lagFeilResultat(fnr, "Eksternt kall mot ${EksternYtelse.PESYS_UFORE} feilet")
            },
            ifRight = {
                mapPesysOppslag(fnr = fnr, dato = dato, perioderForPerson = it.resultat)
            },
        )
    }

    private fun mapPesysOppslag(
        fnr: List<Fnr>,
        dato: LocalDate,
        perioderForPerson: List<PesysPerioderForPerson>,
    ): Map<Fnr, EksterntOppslag> {
        if (fnr.isEmpty()) return emptyMap()

        val defaultResultat: MutableMap<Fnr, EksterntOppslag> = fnr.associateWith {
            EksterntOppslag.IngenTreff
        }.toMutableMap()

        perioderForPerson.forEach { person ->
            val personFnr = Fnr(person.fnr)
            defaultResultat[personFnr] = person.gyldigPå(dato).fold(
                ifLeft = {
                    log.warn("Fradragssjekk: Ugyldig pesys-respons for {}: {}", personFnr, it)
                    EksterntOppslag.Feil(it)
                },
                ifRight = { periode ->
                    periode?.let { EksterntOppslag.Funnet(it.netto.toDouble()) } ?: EksterntOppslag.IngenTreff
                },
            )
        }

        return defaultResultat
    }

    private fun lagFeilResultat(
        fnr: List<Fnr>,
        feilmelding: String,
    ): Map<Fnr, EksterntOppslag> = fnr.associateWith { EksterntOppslag.Feil(feilmelding) }

    private fun hentAapOppslag(
        fnr: List<Fnr>,
        måned: Måned,
    ): Map<Fnr, EksterntOppslag> {
        if (fnr.isEmpty()) return emptyMap()

        val correlationId = getOrCreateCorrelationIdFromThreadLocal().toString()

        return runBlocking {
            fnr.chunked(AAP_PARALLELLE_OPPSLAG)
                .flatMap { fnrChunk ->
                    fnrChunk.map { personFnr ->
                        async(Dispatchers.IO) {
                            withMdcCorrelationId(correlationId) {
                                personFnr to hentAapOppslagForFnr(personFnr, måned)
                            }
                        }
                    }.awaitAll()
                }
                .toMap()
        }
    }

    private fun hentAapOppslagForFnr(
        fnr: Fnr,
        måned: Måned,
    ): EksterntOppslag {
        return aapKlient.hentMaksimum(
            fnr = fnr,
            fraOgMedDato = måned.fraOgMed,
            tilOgMedDato = måned.tilOgMed,
        ).fold(
            ifLeft = {
                log.warn("Fradragssjekk: AAP-oppslag feilet for fnr {}", fnr)
                EksterntOppslag.Feil("AAP-oppslag feilet")
            },
            ifRight = { response ->
                response.vedtak.gyldigAapPå(måned.fraOgMed).fold(
                    ifLeft = {
                        log.warn("Fradragssjekk: Ugyldig AAP-respons for fnr {}: {}", fnr, it)
                        EksterntOppslag.Feil(it)
                    },
                    ifRight = { vedtak ->
                        vedtak?.let { EksterntOppslag.Funnet(it.tilMånedsbeløpForSu().toDouble()) }
                            ?: EksterntOppslag.IngenTreff
                    },
                )
            },
        )
    }
}

private fun List<SjekkPlan>.hentFnrForYtelse(ytelse: EksternYtelse): List<Fnr> {
    return flatMap { it.sjekkpunkter }
        .filter { it.ytelse == ytelse }
        .map { it.fnr }
        .distinct()
}

private inline fun <T> withMdcCorrelationId(
    correlationId: String,
    block: () -> T,
): T {
    val previousCorrelationId = MDC.get(CORRELATION_ID_HEADER)

    return try {
        MDC.put(CORRELATION_ID_HEADER, correlationId)
        block()
    } finally {
        if (previousCorrelationId == null) {
            MDC.remove(CORRELATION_ID_HEADER)
        } else {
            MDC.put(CORRELATION_ID_HEADER, previousCorrelationId)
        }
    }
}

private fun List<PesysPeriode>.gyldigPå(dato: LocalDate): Either<String, PesysPeriode?> {
    val gyldigePerioder = filter {
        !dato.isBefore(it.fom) && (it.tom == null || !dato.isAfter(it.tom))
    }

    return when (gyldigePerioder.size) {
        0 -> Either.Right(null)
        1 -> Either.Right(gyldigePerioder.single())
        else -> Either.Left("Fant flere gyldige perioder på dato $dato")
    }
}

private fun PesysPerioderForPerson.gyldigPå(dato: LocalDate): Either<String, PesysPeriode?> {
    return perioder.gyldigPå(dato)
}

private fun List<MaksimumVedtakDto>.gyldigAapPå(dato: LocalDate): Either<String, MaksimumVedtakDto?> {
    val gyldigeVedtak = filter { vedtak ->
        val periode = vedtak.periode ?: return@filter false
        val fraOgMed = periode.fraOgMedDato ?: return@filter false
        val tilOgMed = periode.tilOgMedDato ?: return@filter false
        vedtak.opphorsAarsak == null && !dato.isBefore(fraOgMed) && !dato.isAfter(tilOgMed)
    }

    return when (gyldigeVedtak.size) {
        0 -> Either.Right(null)
        1 -> Either.Right(gyldigeVedtak.single())
        else -> Either.Left("Fant flere gyldige AAP-vedtak på dato $dato")
    }
}

private fun MaksimumVedtakDto.tilMånedsbeløpForSu(): BigDecimal {
    val dagsats = requireNotNull(dagsats) { "Kan ikke beregne AAP-beløp uten dagsats" }
    return BigDecimal(dagsats)
        .multiply(AAP_STONADSDAGER_PER_AR)
        .divide(AAP_MANEDER_PER_AR, 2, RoundingMode.HALF_UP)
}
