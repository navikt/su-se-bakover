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
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.MaksimumVedtakDto
import no.nav.su.se.bakover.domain.regulering.tilMånedsbeløpForSu
import org.slf4j.Logger
import java.time.LocalDate

private const val AAP_PARALLELLE_OPPSLAG = 8
private const val PESYS_BATCH_STORRELSE = 50

internal class EksterneFradragsoppslagService(
    private val aapKlient: AapApiInternClient,
    private val pesysKlient: PesysClient,
    private val log: Logger,
) {
    fun hentOppslagsresultaterForYtelser(
        sjekkplaner: List<SjekkPlan>,
        måned: Måned,
    ): EksterneOppslagsresultater {
        return EksterneOppslagsresultater(
            aap = hentAapOppslag(sjekkplaner.hentFnrForYtelsePåTversAvSjekkplaner(EksternYtelse.AAP), måned),
            pesysAlder = hentPesysAlderOppslag(sjekkplaner.hentFnrForYtelsePåTversAvSjekkplaner(EksternYtelse.PESYS_ALDER), måned.fraOgMed),
            pesysUføre = hentPesysUføreOppslag(sjekkplaner.hentFnrForYtelsePåTversAvSjekkplaner(EksternYtelse.PESYS_UFORE), måned.fraOgMed),
        )
    }

    private fun hentPesysAlderOppslag(
        fnr: List<Fnr>,
        dato: LocalDate,
    ): Map<Fnr, EksterntOppslag> {
        if (fnr.isEmpty()) return emptyMap()
        log.info("Henter pesys-alder-oppslag for {} personer", fnr.size)
        return hentPesysOppslag(
            fnr = fnr,
            dato = dato,
            ytelse = EksternYtelse.PESYS_ALDER,
        ) { fnrChunk, chunkDato ->
            pesysKlient.hentVedtakForPersonPaaDatoAlder(fnrChunk, chunkDato).fold(
                ifLeft = { Either.Left(it) },
                ifRight = { Either.Right(it.resultat) },
            )
        }
    }

    private fun hentPesysUføreOppslag(
        fnr: List<Fnr>,
        dato: LocalDate,
    ): Map<Fnr, EksterntOppslag> {
        if (fnr.isEmpty()) return emptyMap()

        log.info("Henter pesys-uføre-oppslag for {} personer", fnr.size)

        return hentPesysOppslag(
            fnr = fnr,
            dato = dato,
            ytelse = EksternYtelse.PESYS_UFORE,
        ) { fnrChunk, chunkDato ->
            pesysKlient.hentVedtakForPersonPaaDatoUføre(fnrChunk, chunkDato).fold(
                ifLeft = { Either.Left(it) },
                ifRight = { Either.Right(it.resultat) },
            )
        }
    }

    private fun hentPesysOppslag(
        fnr: List<Fnr>,
        dato: LocalDate,
        ytelse: EksternYtelse,
        hentFraPesys: (List<Fnr>, LocalDate) -> Either<ClientError, List<PesysPerioderForPerson>>,
    ): Map<Fnr, EksterntOppslag> {
        return fnr.chunked(PESYS_BATCH_STORRELSE)
            .fold(mutableMapOf()) { acc, fnrChunk ->
                val resultaterForChunk = hentFraPesys(fnrChunk, dato).fold(
                    ifLeft = {
                        log.warn("Fradragssjekk: Eksternt kall mot {} feilet for {} personer", ytelse, fnrChunk.size)
                        lagFeilResultat(fnrChunk, "Eksternt kall mot $ytelse feilet")
                    },
                    ifRight = {
                        mapPesysOppslag(fnr = fnrChunk, dato = dato, perioderForPerson = it)
                    },
                )

                acc.apply {
                    putAll(resultaterForChunk)
                }
            }
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
                    log.warn("Fradragssjekk: Ugyldig pesys-respons for en person på dato {}", dato)
                    sikkerLogg.warn("Fradragssjekk: Ugyldig pesys-respons for fnr {} på dato {}: {}", personFnr, dato, it)
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
        log.info("Henter AAP-oppslag for {} personer", fnr.size)
        return runBlocking {
            fnr.chunked(AAP_PARALLELLE_OPPSLAG)
                .flatMap { fnrChunk ->
                    fnrChunk.map { personFnr ->
                        async(Dispatchers.IO) {
                            personFnr to hentAapOppslagForFnr(personFnr, måned)
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
        return aapKlient.hentMaksimumUtenUtbetaling(
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

private fun List<SjekkPlan>.hentFnrForYtelsePåTversAvSjekkplaner(ytelse: EksternYtelse): List<Fnr> {
    return flatMap { it.sjekkpunkter }
        .filter { it.ytelse == ytelse }
        .map { it.fnr }
        .distinct()
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
        vedtak.opphorsAarsak == null && !dato.isBefore(fraOgMed) && (periode.tilOgMedDato == null || !dato.isAfter(periode.tilOgMedDato))
    }

    return when (gyldigeVedtak.size) {
        0 -> Either.Right(null)
        1 -> Either.Right(gyldigeVedtak.single())
        else -> Either.Left("Fant flere gyldige AAP-vedtak på dato $dato")
    }
}
