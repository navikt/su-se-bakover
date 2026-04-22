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
import no.nav.su.se.bakover.domain.regulering.erAktivtVedtakPå
import no.nav.su.se.bakover.domain.regulering.tilMånedsbeløpForSu
import org.slf4j.Logger
import java.time.LocalDate
import java.util.UUID

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
        val aapOppslagspersoner = sjekkplaner.hentOppslagspersonerForYtelsePåTversAvSjekkplaner(EksternYtelse.AAP)
        val pesysAlderOppslagspersoner = sjekkplaner.hentOppslagspersonerForYtelsePåTversAvSjekkplaner(EksternYtelse.PESYS_ALDER)
        val pesysUføreOppslagspersoner = sjekkplaner.hentOppslagspersonerForYtelsePåTversAvSjekkplaner(EksternYtelse.PESYS_UFORE)

        return EksterneOppslagsresultater(
            aap = hentAapOppslag(aapOppslagspersoner, måned),
            pesysAlder = hentPesysAlderOppslag(pesysAlderOppslagspersoner, måned.fraOgMed),
            pesysUføre = hentPesysUføreOppslag(pesysUføreOppslagspersoner, måned.fraOgMed),
        )
    }

    private fun hentPesysAlderOppslag(
        oppslagspersoner: List<Oppslagsperson>,
        dato: LocalDate,
    ): Map<Fnr, EksterntOppslag> {
        if (oppslagspersoner.isEmpty()) return emptyMap()
        log.info("Henter pesys-alder-oppslag for {} personer", oppslagspersoner.size)
        return hentPesysOppslag(
            oppslagspersoner = oppslagspersoner,
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
        oppslagspersoner: List<Oppslagsperson>,
        dato: LocalDate,
    ): Map<Fnr, EksterntOppslag> {
        if (oppslagspersoner.isEmpty()) return emptyMap()

        log.info("Henter pesys-uføre-oppslag for {} personer", oppslagspersoner.size)

        return hentPesysOppslag(
            oppslagspersoner = oppslagspersoner,
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
        oppslagspersoner: List<Oppslagsperson>,
        dato: LocalDate,
        ytelse: EksternYtelse,
        hentFraPesys: (List<Fnr>, LocalDate) -> Either<ClientError, List<PesysPerioderForPerson>>,
    ): Map<Fnr, EksterntOppslag> {
        val sakIderPerFnr = oppslagspersoner.tilSakIderPerFnr()

        return oppslagspersoner.map { it.fnr }
            .chunked(PESYS_BATCH_STORRELSE)
            .fold(mutableMapOf()) { acc, fnrChunk ->
                val resultaterForChunk = hentFraPesys(fnrChunk, dato).fold(
                    ifLeft = {
                        log.warn("Fradragssjekk: Eksternt kall mot {} feilet for {} personer", ytelse, fnrChunk.size)
                        lagFeilResultat(fnrChunk, "Eksternt kall mot $ytelse feilet")
                    },
                    ifRight = {
                        mapPesysOppslag(
                            fnr = fnrChunk,
                            dato = dato,
                            perioderForPerson = it,
                            sakIderPerFnr = sakIderPerFnr,
                        )
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
        sakIderPerFnr: Map<Fnr, Set<UUID>>,
    ): Map<Fnr, EksterntOppslag> {
        if (fnr.isEmpty()) return emptyMap()

        val defaultResultat: MutableMap<Fnr, EksterntOppslag> = fnr.associateWith {
            EksterntOppslag.IngenTreff
        }.toMutableMap()

        perioderForPerson.forEach { person ->
            val personFnr = Fnr(person.fnr)
            val sakIder = sakIderPerFnr[personFnr].orEmpty()
            defaultResultat[personFnr] = person.gyldigPå(dato).fold(
                ifLeft = {
                    log.warn("Fradragssjekk: Ugyldig pesys-respons for sakId(er) {} på dato {}", sakIder, dato)
                    sikkerLogg.warn("Fradragssjekk: Ugyldig pesys-respons for sakId(er) {} på dato {}: {}", sakIder, dato, it)
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
        oppslagspersoner: List<Oppslagsperson>,
        måned: Måned,
    ): Map<Fnr, EksterntOppslag> {
        if (oppslagspersoner.isEmpty()) return emptyMap()
        log.info("Henter AAP-oppslag for {} personer", oppslagspersoner.size)

        val sakIderPerFnr = oppslagspersoner.tilSakIderPerFnr()

        return runBlocking {
            oppslagspersoner.map { it.fnr }
                .chunked(AAP_PARALLELLE_OPPSLAG)
                .flatMap { fnrChunk ->
                    fnrChunk.map { personFnr ->
                        async(Dispatchers.IO) {
                            personFnr to hentAapOppslagForFnr(personFnr, måned, sakIderPerFnr[personFnr].orEmpty())
                        }
                    }.awaitAll()
                }
                .toMap()
        }
    }

    private fun hentAapOppslagForFnr(
        fnr: Fnr,
        måned: Måned,
        sakIder: Set<UUID>,
    ): EksterntOppslag {
        return aapKlient.hentMaksimumUtenUtbetaling(
            fnr = fnr,
            fraOgMedDato = måned.fraOgMed,
            tilOgMedDato = måned.tilOgMed,
        ).fold(
            ifLeft = {
                log.warn("Fradragssjekk: AAP-oppslag feilet for sakId(er) {}", sakIder)
                EksterntOppslag.Feil("AAP-oppslag feilet")
            },
            ifRight = { response ->
                response.vedtak.gyldigAapPå(måned.fraOgMed).fold(
                    ifLeft = {
                        log.warn("Fradragssjekk: Ugyldig AAP-respons for sakId(er) {}: {}", sakIder, it)
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

private data class Oppslagsperson(
    val fnr: Fnr,
    val sakIder: Set<UUID>,
)

private fun List<SjekkPlan>.hentOppslagspersonerForYtelsePåTversAvSjekkplaner(
    ytelse: EksternYtelse,
): List<Oppslagsperson> {
    val sakIderPerFnr = mutableMapOf<Fnr, MutableSet<UUID>>()

    forEach { sjekkplan ->
        sjekkplan.sjekkpunkter
            .filter { it.ytelse == ytelse }
            .forEach { sjekkpunkt ->
                sakIderPerFnr
                    .getOrPut(sjekkpunkt.fnr) { mutableSetOf() }
                    .add(sjekkplan.sak.sakId)
            }
    }

    return sakIderPerFnr.map { (fnr, sakIder) ->
        Oppslagsperson(
            fnr = fnr,
            sakIder = sakIder,
        )
    }
}

private fun List<Oppslagsperson>.tilSakIderPerFnr(): Map<Fnr, Set<UUID>> {
    return associate { it.fnr to it.sakIder }
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
    val gyldigeVedtak = filter { it.erAktivtVedtakPå(dato) }

    return when (gyldigeVedtak.size) {
        0 -> Either.Right(null)
        1 -> Either.Right(gyldigeVedtak.single())
        else -> Either.Left("Fant flere gyldige AAP-vedtak på dato $dato vedtak: $gyldigeVedtak")
    }
}
