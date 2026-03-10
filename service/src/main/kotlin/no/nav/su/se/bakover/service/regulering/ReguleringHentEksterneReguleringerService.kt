package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.flatten
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperioderPerPerson
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysPerioderForPerson
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperiode
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
     * Hver relevante periode blir mappet til ønsket beløp og verifisert at er i riktig tilstand.
     * Se [utledOgVerifiserRegulertFradrag].
     */
    private fun utledRegulerteFradragForBrukerMedEps(
        brukereMedEps: List<BrukerMedEps>,
        perioderFraPesys: List<PesysPerioderForPerson>,
        månedFørRegulering: LocalDate,
    ): List<Either<HentingAvRegulerteFradragFeiletForBruker, RegulerteFradragEksternKilde>> {
        return brukereMedEps.map { brukerMedEps ->

            // TODO må hente forventet inntekt fra uføregrunnlag her?

            val fradragFraPesysBruker = brukerMedEps.bruker.fradrag.map { bruktFradrag ->
                utledOgVerifiserRegulertFradrag(
                    brukerMedEps.bruker.fnr,
                    bruktFradrag,
                    perioderFraPesys = perioderFraPesys,
                    månedFørRegulering = månedFørRegulering,
                )
            }
            val fradragFraPesysEps = brukerMedEps.eps?.fradrag?.map { bruktFradrag ->
                utledOgVerifiserRegulertFradrag(
                    brukerMedEps.eps.fnr,
                    bruktFradrag,
                    perioderFraPesys = perioderFraPesys,
                    månedFørRegulering = månedFørRegulering,
                )
            }

            val alleFeil = listOfNotNull(fradragFraPesysBruker, fradragFraPesysEps).flatten().filterLefts()
            if (alleFeil.isNotEmpty()) {
                HentingAvRegulerteFradragFeiletForBruker(
                    fnr = brukerMedEps.bruker.fnr,
                    alleFeil = alleFeil,
                ).left()
            } else {
                RegulerteFradragEksternKilde(
                    fnr = brukerMedEps.bruker.fnr,
                    bruker = fradragFraPesysBruker.map { it.getOrElse { throw IllegalStateException("$it skal returneres som left før dette stadiet!") } },
                    forEps = fradragFraPesysEps?.map { it.getOrElse { throw IllegalStateException("$it skal returneres som left før dette stadiet!") } }
                        ?: emptyList(),
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
        // TODO OBS - må muligens endres hvis AAP skal inn her?
        val forventetPesysPerioder = perioderFraPesys.singleOrNull { Fnr(it.fnr) == fnr }
        if (forventetPesysPerioder == null) {
            log.error("Fant ingen perioder fra Pesys for bruker med forventet regulert fradrag. Se sikkerlogg for detaljer.")
            sikkerLogg.error("Fant ingen perioder fra Pesys for bruker med forventet regulert fradrag. Bruker=$fnr")
            return IngenPeriodeFraPesys.left()
        }

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

        if (fradrag.fradragstype != Fradragstype.ForventetInntekt) {
            return RegulertFradragEksternKilde(
                fnr = fnr,
                førRegulering = forventetPesysPerioder.perioder[0].netto,
                etterRegulering = forventetPesysPerioder.perioder[1].netto,
            ).right()
        } else {
            val inntektEtterUføreFørRegulering =
                (forventetPesysPerioder.perioder[0] as UføreBeregningsperiode).oppjustertInntektEtterUfore
            val inntektEtterUføreEtterRegulering =
                (forventetPesysPerioder.perioder[1] as UføreBeregningsperiode).oppjustertInntektEtterUfore

            return RegulertFradragEksternKilde(
                fnr = fnr,
                førRegulering = inntektEtterUføreFørRegulering ?: 0,
                etterRegulering = inntektEtterUføreEtterRegulering ?: 0,
                manueltIeu = inntektEtterUføreFørRegulering == null || inntektEtterUføreEtterRegulering == null,
            ).right()
        }
    }

    private fun hentPerioderUføre(
        brukereMedEps: List<BrukerMedEps>,
        dato: LocalDate,
    ): List<UføreBeregningsperioderPerPerson> {
        val unikeFnr =
            brukereMedEps.unikeFnrSomBenytterFradragstype(
                listOf(
                    Fradragstype.ForventetInntekt,
                    Fradragstype.Uføretrygd,
                ),
            ) // TODO er må Forventet inntekt inn for bruker?
        return pesysClient.hentVedtakForPersonPaaDatoUføre(
            fnrList = unikeFnr,
            dato = dato,
        ).getOrElse {
            throw UthentingAvPerioderUføreFeilet()
        }.resultat
    }

    private fun hentPerioderAlder(
        brukereMedEps: List<BrukerMedEps>,
        dato: LocalDate,
    ): List<AlderBeregningsperioderPerPerson> {
        val unikeFnr = brukereMedEps.unikeFnrSomBenytterFradragstype(listOf(Fradragstype.Alderspensjon))
        return pesysClient.hentVedtakForPersonPaaDatoAlder(
            fnrList = unikeFnr,
            dato = dato,
        ).getOrElse {
            throw UthentingAvPerioderAlderFeilet()
        }.resultat
    }

    private fun List<BrukerMedEps>.unikeFnrSomBenytterFradragstype(fradragstyper: List<Fradragstype>): List<Fnr> =
        flatMap { listOfNotNull(it.bruker, it.eps) }
            .filter { person -> person.fradrag.any { fradragstyper.contains(it.fradragstype) } }
            .map { it.fnr }
            .distinct()
}

/**
 * Objekt for å hente regulerte beløper som skal brukes som fradrag.
 * Basert på reguleringsmåned og en liste saker utledes alle brukere og eps'er som har fradrag som kan hentes eksternt.
 * Bruker vil alltid ha minst en fradragstype som kan hentes eksternt (inntekt etter uføre eller alderspensjon).
 * Eps vil kunne ha 0 relevante fradragstyper. Da vil liste med fradrag være tom.
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
            // Fradragstype.Arbeidsavklaringspenger, TODO ??

            // OBS! Ligger ikke i fradragsgrunnlag men må utledes fra uførevilkår
            Fradragstype.ForventetInntekt,
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
            val vedtaksdata = hentGjeldendeVedtaksdata(reguleringsMåned, clock).getOrElse {
                throw IllegalStateException("Kan ikke hente eksterne fradrag for sak som ikke er løpende")
            }
            val grunnlagsdata = vedtaksdata.grunnlagsdata
            return BrukerMedEps(
                // TODO AUTO-REG-26 - Legge til Forventet Inntekt som fradrag (IEU) hvis uføre. Se BeregningStrategyFactory.beregn
                bruker = PersonMedFradrag(
                    fnr = fnr,
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
}

class UthentingAvPerioderUføreFeilet : IllegalStateException()
class UthentingAvPerioderAlderFeilet : IllegalStateException()
