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
import no.nav.su.se.bakover.service.regulering.FeilMedRegulertFradrag.IngenPeriodeFraPesys
import no.nav.su.se.bakover.service.regulering.HentEksterneReguleringerRequest.BrukerMedEps
import no.nav.su.se.bakover.service.regulering.HentEksterneReguleringerRequest.BrukerMedFradrag
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.Fradrag
import java.time.Clock
import java.time.LocalDate
import kotlin.collections.List
import kotlin.collections.flatMap
import kotlin.collections.map
import kotlin.collections.singleOrNull

class ReguleringHentEksterneReguleringerService(
    private val pesysClient: PesysClient,
    private val satsFactory: SatsFactory,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    // TODO AUTO-REG-26 feilhåndtering..
    fun hentEksterneReguleringer(request: HentEksterneReguleringerRequest): List<Either<HentingAvRegulerteFradragFeiletForBruker, RegulerteFradragEksternKilde>> {
        val (månedFørRegulering, brukereMedEpsUføre, brukereMedEpsAlder) = request

        // TODO må kartlegge hvilke eps som er alder/uføre enten før kall eller ved utledning av pesysperioder
        // TODO hvis førstnevnte må begge kalle få liste med ALLE eps ikke bare til der bruker er alder/uføre

        val uførePerioder = hentPerioderUføre(brukereMedEpsUføre, månedFørRegulering)
        val uførefradrag = utledRegulerteFradragForBrukerMedEps(
            brukereMedEps = brukereMedEpsUføre,
            perioderFraPesys = uførePerioder,
            månedFørRegulering = månedFørRegulering,
        )

        val alderPerioder = hentPerioderAlder(brukereMedEpsAlder, månedFørRegulering)
        val alderfradrag = utledRegulerteFradragForBrukerMedEps(
            brukereMedEps = brukereMedEpsAlder,
            perioderFraPesys = alderPerioder,
            månedFørRegulering = månedFørRegulering,
        )

        return uførefradrag + alderfradrag
    }

    /**
     * Finner perioder fra Pesys som skal brukes som fradrag for en angitt bruker og dens eps
     * Hvert relevante peruode blir mappet til ønsket beløper og verifisert at er i riktig tilstand.
     * Se [utledOgVerifiserRegulertFradrag].
     */
    private fun utledRegulerteFradragForBrukerMedEps(
        brukereMedEps: List<BrukerMedEps>,
        perioderFraPesys: List<PesysPerioderForPerson>,
        månedFørRegulering: LocalDate,
    ): List<Either<HentingAvRegulerteFradragFeiletForBruker, RegulerteFradragEksternKilde>> {
        return brukereMedEps.map { brukerMedEps ->
            // TODO vil alder være et fradrag her? Slik som ieu???
            val eksterneFradragBruker = utledOgVerifiserRegulertFradrag(
                bruker = brukerMedEps.bruker,
                perioderFraPesys = perioderFraPesys,
                månedFørRegulering = månedFørRegulering,
            )
            val eksterneFradragEps = brukerMedEps.eps.map { eps ->
                utledOgVerifiserRegulertFradrag(
                    bruker = eps,
                    perioderFraPesys = perioderFraPesys,
                    månedFørRegulering = månedFørRegulering,
                )
            }

            val alleFeil = (listOf(eksterneFradragBruker) + eksterneFradragEps).filterLefts()
            if (alleFeil.isNotEmpty()) {
                HentingAvRegulerteFradragFeiletForBruker(
                    fnr = brukerMedEps.bruker.fnr,
                    alleFeil = alleFeil,
                ).left()
            } else {
                RegulerteFradragEksternKilde(
                    bruker = eksterneFradragBruker.getOrElse { throw IllegalStateException("$it skal returneres som left før dette stadiet!") },
                    forEps = eksterneFradragEps.map { it.getOrElse { throw IllegalStateException("$it skal returneres som left før dette stadiet!") } },
                ).right()
            }
        }
    }

    private fun utledOgVerifiserRegulertFradrag(
        bruker: BrukerMedFradrag,
        perioderFraPesys: List<PesysPerioderForPerson>,
        månedFørRegulering: LocalDate,
    ): Either<FeilMedRegulertFradrag, RegulertFradragEksternKilde> {
        // TODO Må være avklart at det er forventet å ha perioder i Pesys
        val forventetPesysPerioder = perioderFraPesys.singleOrNull { Fnr(it.fnr) == bruker.fnr }
        if (forventetPesysPerioder == null) {
            log.error("Fant ingen perioder fra Pesys for bruker med forventet regulert fradrag. Se sikkerlogg for detaljer.")
            sikkerLogg.error("Fant ingen perioder fra Pesys for bruker med forventet regulert fradrag. Bruker=${bruker.fnr}")
            return IngenPeriodeFraPesys.left()
        }

        // TODO AUTO-REG-26 ta i bruk inntekt etter uføre hvis uføretrygd

        if (forventetPesysPerioder.perioder.size != 2) {
            return FeilMedRegulertFradrag.ManglerPeriodeFørOgEtterReguleringFraPesys.left()
        }

        val førRegulering = forventetPesysPerioder.perioder[0]
        val forventetGammelG = satsFactory.grunnbeløp(månedFørRegulering).grunnbeløpPerÅr
        if (førRegulering.grunnbelop != forventetGammelG) {
            return FeilMedRegulertFradrag.GrunnbeløpFraPesysUliktForventetGammelt.left()
        }

        val etterRegulering = forventetPesysPerioder.perioder[1]
        val forventetNyG = satsFactory.grunnbeløp(månedFørRegulering.plusMonths(1)).grunnbeløpPerÅr
        if (etterRegulering.grunnbelop != forventetNyG) {
            return FeilMedRegulertFradrag.GrunnbeløpFraPesysUliktForventetNytt.left()
        }
        val bruktFradrag =
            bruker.fradrag.first() // TODO Må forsikre at riktig fradrag på dette stadiet - kan det være flere perioder????
        if (førRegulering.netto.toDouble() != bruktFradrag.månedsbeløp) {
            return FeilMedRegulertFradrag.BeløpFraPesysErUliktBenyttetFradrag.left()
        }

        return RegulertFradragEksternKilde(
            fnr = bruker.fnr,
            førRegulering = forventetPesysPerioder.perioder[0].netto,
            etterRegulering = forventetPesysPerioder.perioder[1].netto,
        ).right()
    }

    private fun hentPerioderUføre(
        brukereMedEps: List<BrukerMedEps>,
        dato: LocalDate,
    ): List<UføreBeregningsperioderPerPerson> {
        val unikeFnr = brukereMedEps.listeAlleUnikeFnr()
        return pesysClient.hentVedtakForPersonPaaDatoUføre(
            fnrList = unikeFnr,
            dato = dato,
        ).getOrElse {
            throw UtehentingAvPerioderUføreFeilet()
        }.resultat
    }

    private fun hentPerioderAlder(
        brukereMedEps: List<BrukerMedEps>,
        dato: LocalDate,
    ): List<AlderBeregningsperioderPerPerson> {
        val unikeFnr = brukereMedEps.listeAlleUnikeFnr()
        return pesysClient.hentVedtakForPersonPaaDatoAlder(
            fnrList = unikeFnr,
            dato = dato,
        ).getOrElse {
            throw UtehentingAvPerioderAlderFeilet()
        }.resultat
    }

    private fun List<BrukerMedEps>.listeAlleUnikeFnr(): List<Fnr> =
        this.flatMap { listOf(it.bruker.fnr) + it.eps.map { it.fnr } }.distinct()
}

data class HentEksterneReguleringerRequest(
    val månedFørRegulering: LocalDate,
    val brukereMedEpsUføre: List<BrukerMedEps>,
    val brukereMedEpsAlder: List<BrukerMedEps>,
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

data class HentingAvRegulerteFradragFeiletForBruker(
    val fnr: Fnr,
    val alleFeil: List<FeilMedRegulertFradrag>,
)

interface FeilMedRegulertFradrag {
    object IngenPeriodeFraPesys : FeilMedRegulertFradrag
    object ManglerPeriodeFørOgEtterReguleringFraPesys : FeilMedRegulertFradrag
    object GrunnbeløpFraPesysUliktForventetGammelt : FeilMedRegulertFradrag
    object GrunnbeløpFraPesysUliktForventetNytt : FeilMedRegulertFradrag

    // Dette skal føre til revurdering med vedtaksbrev
    object BeløpFraPesysErUliktBenyttetFradrag : FeilMedRegulertFradrag
}

class UtehentingAvPerioderUføreFeilet : IllegalStateException()
class UtehentingAvPerioderAlderFeilet : IllegalStateException()
