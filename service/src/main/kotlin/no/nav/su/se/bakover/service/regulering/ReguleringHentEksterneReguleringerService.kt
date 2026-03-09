package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.flatten
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperioderPerPerson
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysPerioderForPerson
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperioderPerPerson
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.RegulertFradragEksternKilde
import no.nav.su.se.bakover.domain.regulering.RegulerteFradragEksternKilde
import no.nav.su.se.bakover.service.regulering.FeilMedRegulertFradrag.IngenPeriodeFraPesys
import no.nav.su.se.bakover.service.regulering.HentEksterneReguleringerRequest.BrukerMedEps
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
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
        val (månedFørRegulering, brukereMedEps) = request

        val uførePerioder = hentPerioderUføre(brukereMedEps, månedFørRegulering)
        val alderPerioder = hentPerioderAlder(brukereMedEps, månedFørRegulering)

        return utledRegulerteFradragForBrukerMedEps(
            brukereMedEps = brukereMedEps,
            perioderFraPesys = uførePerioder + alderPerioder,
            månedFørRegulering = månedFørRegulering,
        )
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

            val eksterneFradragBruker = brukerMedEps.bruker.fradrag.map { bruktFradrag ->
                utledOgVerifiserRegulertFradrag(
                    brukerMedEps.bruker.fnr,
                    bruktFradrag,
                    perioderFraPesys = perioderFraPesys,
                    månedFørRegulering = månedFørRegulering,
                )
            }
            val eksterneFradragEps = brukerMedEps.eps?.fradrag?.map { bruktFradrag ->
                utledOgVerifiserRegulertFradrag(
                    brukerMedEps.eps.fnr,
                    bruktFradrag,
                    perioderFraPesys = perioderFraPesys,
                    månedFørRegulering = månedFørRegulering,
                )
            }

            val alleFeil = listOfNotNull(eksterneFradragBruker, eksterneFradragEps).flatten().filterLefts()
            if (alleFeil.isNotEmpty()) {
                HentingAvRegulerteFradragFeiletForBruker(
                    fnr = brukerMedEps.bruker.fnr,
                    alleFeil = alleFeil,
                ).left()
            } else {
                RegulerteFradragEksternKilde(
                    bruker = eksterneFradragBruker.map { it.getOrElse { throw IllegalStateException("$it skal returneres som left før dette stadiet!") } }.single(), // TODO fjern single
                    forEps = eksterneFradragEps?.map { it.getOrElse { throw IllegalStateException("$it skal returneres som left før dette stadiet!") } } ?: emptyList(),
                ).right()
            }
        }
    }

    private fun utledOgVerifiserRegulertFradrag(
        fnr: Fnr,
        fradrag: Fradrag,
        perioderFraPesys: List<PesysPerioderForPerson>,
        månedFørRegulering: LocalDate,
    ): Either<FeilMedRegulertFradrag, RegulertFradragEksternKilde> {
        // TODO Må være avklart at det er forventet å ha perioder i Pesys
        val forventetPesysPerioder = perioderFraPesys.singleOrNull { Fnr(it.fnr) == fnr }
        if (forventetPesysPerioder == null) {
            log.error("Fant ingen perioder fra Pesys for bruker med forventet regulert fradrag. Se sikkerlogg for detaljer.")
            sikkerLogg.error("Fant ingen perioder fra Pesys for bruker med forventet regulert fradrag. Bruker=$fnr")
            return IngenPeriodeFraPesys.left()
        }

        // TODO AUTO-REG-26 ta i bruk inntekt etter uføre hvis uføretrygd - instance of etc

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
        if (førRegulering.netto.toDouble() != fradrag.månedsbeløp) {
            return FeilMedRegulertFradrag.BeløpFraPesysErUliktBenyttetFradrag.left()
        }

        return RegulertFradragEksternKilde(
            fnr = fnr,
            førRegulering = forventetPesysPerioder.perioder[0].netto,
            etterRegulering = forventetPesysPerioder.perioder[1].netto,
        ).right()
    }

    private fun hentPerioderUføre(
        brukereMedEps: List<BrukerMedEps>,
        dato: LocalDate,
    ): List<UføreBeregningsperioderPerPerson> {
        val unikeFnr =
            brukereMedEps.unikeFnrSomBenytterFradragstype(Fradragstype.Uføretrygd) // TODO er må Forventet inntekt inn for bruker?
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
        val unikeFnr = brukereMedEps.unikeFnrSomBenytterFradragstype(Fradragstype.Alderspensjon)
        return pesysClient.hentVedtakForPersonPaaDatoAlder(
            fnrList = unikeFnr,
            dato = dato,
        ).getOrElse {
            throw UtehentingAvPerioderAlderFeilet()
        }.resultat
    }

    private fun List<BrukerMedEps>.unikeFnrSomBenytterFradragstype(fradragstype: Fradragstype): List<Fnr> =
        flatMap { listOfNotNull(it.bruker, it.eps) }
            .filter { person -> person.fradrag.any { it.fradragstype == fradragstype } }
            .map { it.fnr }
            .distinct()
}

/**
 * Objekt for å finne hente regulerte beløper som skal brukes som fradrag.
 * Basert på reguleringsmåned og en liste saker utledes alle brukere og eps'er som har fradrag som kan hentes eksternt.
 * Bruker vil alltid ha minst en fradragstype som kan hentes eksternt (inntekt etter uføre eller alderspensjon).
 * Eps vil kunne ha 0 relevante fradragstyper. Da vi list med fradrag være tom.
 */
data class HentEksterneReguleringerRequest(
    val månedFørRegulering: LocalDate,
    val brukereMedEps: List<BrukerMedEps>,
) {

    data class BrukerMedEps(
        val bruker: PersonMedFradrag,
        val eps: PersonMedFradrag?,
    )

    data class PersonMedFradrag(
        val fnr: Fnr,
        val fradrag: List<Fradrag>,
    )

    companion object {
        private val relevanteFradragsTyper = listOf(
            Fradragstype.Alderspensjon,
            Fradragstype.Uføretrygd,
            // Fradragstype.ForventetInntekt, TODO
            // Fradragstype.Arbeidsavklaringspenger, TODO
        )
        fun toRequest(
            reguleringsMåned: Måned,
            forSaker: List<Sak>,
            clock: Clock,
        ): HentEksterneReguleringerRequest {
            return HentEksterneReguleringerRequest(
                månedFørRegulering = reguleringsMåned.fraOgMed.minusMonths(1),
                brukereMedEps = forSaker.map { it.toBrukerMedEps(reguleringsMåned, clock) },
            )
        }

        private fun Sak.toBrukerMedEps(
            reguleringsMåned: Måned,
            clock: Clock,
        ): BrukerMedEps {
            val grunnlagsdata = hentGjeldendeVedtaksdata(reguleringsMåned, clock).getOrElse {
                throw IllegalStateException("Kan ikke hente eksterne fradrag for sak som ikke er løpende")
            }.grunnlagsdata
            return BrukerMedEps(
                bruker = PersonMedFradrag(
                    fnr = fnr,
                    // TODO her skal det vel være forventet inntekt ikke Uføretrygd?
                    fradrag = grunnlagsdata.hentFradragBasertPå(
                        fradragstyper = relevanteFradragsTyper,
                        måned = reguleringsMåned,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                eps = grunnlagsdata.epsForMåned()[reguleringsMåned]?.let {
                    PersonMedFradrag(
                        fnr = it,
                        fradrag = grunnlagsdata.hentFradragBasertPå(
                            fradragstyper = relevanteFradragsTyper,
                            måned = reguleringsMåned,
                            tilhører = FradragTilhører.EPS,
                        ),
                    )
                },
            )
        }
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
