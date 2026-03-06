package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperioderPerPerson
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysPerioderForPerson
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperioderPerPerson
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.RegulertFradragEksternKilde
import no.nav.su.se.bakover.domain.regulering.RegulerteFradragEksternKilde
import no.nav.su.se.bakover.service.regulering.HentEksterneReguleringerRequest.BrukerMedEps
import org.slf4j.LoggerFactory
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock
import java.time.LocalDate
import kotlin.collections.List
import kotlin.collections.flatMap
import kotlin.collections.map

class ReguleringHentEksterneReguleringerService(private val pesysClient: PesysClient) {

    private val log = LoggerFactory.getLogger(this::class.java)

    // TODO AUTO-REG-26 feilhåndtering..
    fun hentEksterneReguleringer(request: HentEksterneReguleringerRequest): List<Either<HentingAvRegulerteFradragFeiletForBruker, RegulerteFradragEksternKilde>> {
        val (månedFørRegulering, brukereMedEpsUføre, brukereMedEpsAlder) = request

        // TODO må kartlegge hvilke eps som er alder/uføre enten før kall eller ved utledning av pesysperioder
        // TODO hvis førstnevnte må begge kalle få liste med ALLE eps ikke bare til der bruker er alder/uføre

        val uførePerioder = hentPerioderUføre(brukereMedEpsUføre.listeAlleUnikeFnr(), månedFørRegulering)
        val uførefradrag = utledRegulerteFradragForBrukerMedEps(
            brukereMedEps = brukereMedEpsUføre,
            perioder = uførePerioder,
        )

        val alderPerioder = hentPerioderAlder(brukereMedEpsAlder.listeAlleUnikeFnr(), månedFørRegulering)
        val alderfradrag = utledRegulerteFradragForBrukerMedEps(
            brukereMedEps = brukereMedEpsAlder,
            perioder = alderPerioder,
        )

        return uførefradrag + alderfradrag
    }

    private fun utledRegulerteFradragForBrukerMedEps(
        brukereMedEps: List<BrukerMedEps>,
        perioder: List<PesysPerioderForPerson>,
    ): List<Either<HentingAvRegulerteFradragFeiletForBruker, RegulerteFradragEksternKilde>> {
        return brukereMedEps.map { brukerMedEps ->
            Either.catch {
                val eksterneFradragBruker = fraPesysPeriodeTilFradrag(
                    // TODO vil alder være et fradrag her? Slik som ieu???
                    pesysPeriode = perioder.utledRegulerteFradragFraPerioder(brukerMedEps.bruker.fnr),
                    bruktFradrag = brukerMedEps.bruker.fradrag.first(), // TODO Må forsikre at riktig fradrag på dette stadiet
                )
                val eksterneFradragEps = brukerMedEps.eps.map { eps ->
                    fraPesysPeriodeTilFradrag(
                        pesysPeriode = perioder.utledRegulerteFradragFraPerioder(eps.fnr),
                        bruktFradrag = eps.fradrag.first(), // TODO Må forsikre at riktig fradrag på dette stadiet
                    )
                }

                if (eksterneFradragBruker.isLeft() || eksterneFradragEps.any { it.isLeft() }) {
                    HentingAvRegulerteFradragFeiletForBruker.BrukerEllerEpsHarFeilMedFradrag(
                        fnr = brukerMedEps.bruker.fnr,
                        alleFeil = (listOf(eksterneFradragBruker) + eksterneFradragEps).filterLefts(),
                    ).left()
                } else {
                    RegulerteFradragEksternKilde(
                        // TODO Litt grisete dette..
                        bruker = eksterneFradragBruker.getOrNull()!!,
                        forEps = eksterneFradragEps.map { it.getOrNull()!! },
                    ).right()
                }
            }.getOrElse {
                HentingAvRegulerteFradragFeiletForBruker.BrukerEllerEpsManglerForventetPesysPeriode(brukerMedEps.bruker.fnr)
                    .left()
            }
        }
    }

    private fun List<PesysPerioderForPerson>.utledRegulerteFradragFraPerioder(fnr: Fnr): PesysPerioderForPerson {
        // TODO Må være avklart at det er forventet å ha perioder i Pesys
        val forventetPesysPerioder = this.singleOrNull { Fnr(it.fnr) == fnr }
        if (forventetPesysPerioder == null) {
            log.error("Fant ingen perioder fra Pesys for bruker med forventet regulert fradrag. Se sikkerlogg for detaljer.")
            sikkerLogg.error("Fant ingen perioder fra Pesys for bruker med forventet regulert fradrag. Bruker=$fnr")
            throw IngenPesysPerioderFunnet()
        }
        return forventetPesysPerioder
    }

    private fun fraPesysPeriodeTilFradrag(
        pesysPeriode: PesysPerioderForPerson,
        bruktFradrag: Fradrag,
        forventetGammelG: Int = 0, // TODO Må utledes fra satsFactory eller lignende.. kanskje på klassenivå?
        forventetNyG: Int = 0,
    ): Either<FeilMedRegulertFradrag, RegulertFradragEksternKilde> {
        when (pesysPeriode) {
            is AlderBeregningsperioderPerPerson -> require(bruktFradrag.fradragstype == Fradragstype.Alderspensjon)
            is UføreBeregningsperioderPerPerson -> require(bruktFradrag.fradragstype == Fradragstype.Uføretrygd)
        }

        // TODO AUTO-REG-26 ta i bruk inntekt etter uføre hvis uføretrygd

        if (pesysPeriode.perioder.size != 2) {
            return FeilMedRegulertFradrag.ManglerPeriodeFørOgEtterReguleringFraPesys.left()
        }
        val førRegulering = pesysPeriode.perioder[0]
        if (førRegulering.grunnbelop != forventetGammelG) {
            return FeilMedRegulertFradrag.GrunnbeløpFraPesysUliktForventetGammelt.left()
        }
        val etterRegulering = pesysPeriode.perioder[1]
        if (etterRegulering.grunnbelop != forventetNyG) {
            return FeilMedRegulertFradrag.GrunnbeløpFraPesysUliktForventetNytt.left()
        }
        if (førRegulering.netto.toDouble() != bruktFradrag.månedsbeløp) {
            return FeilMedRegulertFradrag.BeløpFraPesysErUliktBenyttetFradrag.left()
        }

        return RegulertFradragEksternKilde(
            fnr = Fnr(pesysPeriode.fnr),
            førRegulering = pesysPeriode.perioder[0].netto,
            etterRegulering = pesysPeriode.perioder[1].netto,
        ).right()
    }

    private fun hentPerioderUføre(fnrList: List<Fnr>, dato: LocalDate) =
        pesysClient.hentVedtakForPersonPaaDatoUføre(
            fnrList = fnrList,
            dato = dato,
        ).getOrElse {
            throw UtehentingAvPerioderUføreFeilet()
        }.resultat

    private fun hentPerioderAlder(fnrList: List<Fnr>, dato: LocalDate) =
        pesysClient.hentVedtakForPersonPaaDatoAlder(
            fnrList = fnrList,
            dato = dato,
        ).getOrElse {
            throw UtehentingAvPerioderAlderFeilet()
        }.resultat
}

data class HentEksterneReguleringerRequest(
    val månedFørRegulering: LocalDate,
    val brukereMedEpsUføre: List<BrukerMedEps>,
    val brukereMedEpsAlder: List<BrukerMedEps>,
    val forventetGammelG: Int = 0, // TODO Må utledes fra satsFactory eller lignende.. kanskje på klassenivå?
    val forventetNyG: Int = 0,
) {

    // TODO må legge til fradrag for å kunne avgjøre om det skal hentes fra uføre eller aldre og diffe med pesys perioder
    data class BrukerMedEps(
        val bruker: BrukerMedFradrag,
        val eps: List<BrukerMedFradrag>,
    )

    data class BrukerMedFradrag(
        val fnr: Fnr,
        val fradrag: List<Fradrag>,
    )

    companion object {
        fun toRequest(
            reguleringsMåned: Måned,
            forSaker: List<Sak>,
            clock: Clock,
        ): HentEksterneReguleringerRequest {
            val (uføreSaker, alderSaker) = forSaker.partition { it.type == Sakstype.UFØRE }
            return HentEksterneReguleringerRequest(
                månedFørRegulering = reguleringsMåned.fraOgMed.minusMonths(1),
                brukereMedEpsUføre = uføreSaker.map { it.toBrukerMedEps(reguleringsMåned, clock) },
                brukereMedEpsAlder = alderSaker.map { it.toBrukerMedEps(reguleringsMåned, clock) },
            )
        }

        // TODO AUTO-REG-26 - Må angi fradragene
        private fun Sak.toBrukerMedEps(
            reguleringsMåned: Måned,
            clock: Clock,
        ) = BrukerMedEps(
            bruker = BrukerMedFradrag(
                fnr = fnr,
                fradrag = emptyList(),
            ),
            eps = hentGjeldendeVedtaksdata(reguleringsMåned, clock).getOrNull()?.grunnlagsdata?.eps?.map {
                BrukerMedFradrag(
                    fnr = it,
                    fradrag = emptyList(),
                )
            } ?: emptyList(),
        )
    }
}

private fun List<BrukerMedEps>.listeAlleUnikeFnr(): List<Fnr> =
    this.flatMap { listOf(it.bruker.fnr) + it.eps.map { it.fnr } }.distinct()

open class HentingAvRegulerteFradragFeiletForBruker(
    open val fnr: Fnr,
) {
    data class BrukerEllerEpsManglerForventetPesysPeriode(
        override val fnr: Fnr,
    ) : HentingAvRegulerteFradragFeiletForBruker(fnr)

    data class BrukerEllerEpsHarFeilMedFradrag(
        override val fnr: Fnr,
        val alleFeil: List<FeilMedRegulertFradrag>,
    ) : HentingAvRegulerteFradragFeiletForBruker(fnr)
}

interface FeilMedRegulertFradrag {
    object ManglerPeriodeFørOgEtterReguleringFraPesys : FeilMedRegulertFradrag
    object GrunnbeløpFraPesysUliktForventetGammelt : FeilMedRegulertFradrag
    object GrunnbeløpFraPesysUliktForventetNytt : FeilMedRegulertFradrag

    // Dette skal føre til revurdering med vedtaksbrev
    object BeløpFraPesysErUliktBenyttetFradrag : FeilMedRegulertFradrag
}

class IngenPesysPerioderFunnet : IllegalStateException()

class UtehentingAvPerioderUføreFeilet : IllegalStateException()
class UtehentingAvPerioderAlderFeilet : IllegalStateException()
