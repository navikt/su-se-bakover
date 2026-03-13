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
import no.nav.su.se.bakover.domain.regulering.FeilMedRegulertFradrag
import no.nav.su.se.bakover.domain.regulering.HentEksterneReguleringerRequest
import no.nav.su.se.bakover.domain.regulering.HentEksterneReguleringerRequest.BrukerMedEps
import no.nav.su.se.bakover.domain.regulering.HentingAvRegulerteFradragFeiletForBruker
import no.nav.su.se.bakover.domain.regulering.ReguleringHentEksterneReguleringerService
import no.nav.su.se.bakover.domain.regulering.RegulertFradragEksternKilde
import no.nav.su.se.bakover.domain.regulering.RegulerteFradragEksternKilde
import no.nav.su.se.bakover.domain.regulering.UthentingAvPerioderAlderFeilet
import no.nav.su.se.bakover.domain.regulering.UthentingAvPerioderUføreFeilet
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.LocalDate
import kotlin.collections.List
import kotlin.collections.flatMap
import kotlin.collections.map

class ReguleringHentEksterneReguleringerServiceImpl(
    private val pesysClient: PesysClient,
    private val satsFactory: SatsFactory,
) : ReguleringHentEksterneReguleringerService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentEksterneReguleringer(request: HentEksterneReguleringerRequest): List<Either<HentingAvRegulerteFradragFeiletForBruker, RegulerteFradragEksternKilde>> {
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
            val fradragFraPesysBruker = brukerMedEps.bruker.fradrag.map { bruktFradrag ->
                utledOgVerifiserRegulertFradrag(
                    brukerMedEps.bruker.fnr,
                    bruktFradrag,
                    perioderFraPesys = perioderFraPesys,
                    månedFørRegulering = månedFørRegulering,
                )
            }
            val eps = brukerMedEps.eps
            val fradragFraPesysEps = eps?.fradrag?.map { bruktFradrag ->
                utledOgVerifiserRegulertFradrag(
                    eps.fnr,
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
        val forventetPesysPeriode = perioderFraPesys.filter { Fnr(it.fnr) == fnr }
        if (forventetPesysPeriode.size > 1) {
            // Dette skal ikke kune skje da en bruker skal ikke kunne ha uføretrygd og alderspensjon samtidig.
            log.error("To pesysperioder for samme person som ikke skal være mulig. Sikkerlogg for å se fnr")
            sikkerLogg.error("To pesysperioder for samme person som ikke skal være mulig. Bruker=$fnr")
            return FeilMedRegulertFradrag.OverlappendePeriodeFraPesys.left()
        }
        if (forventetPesysPeriode.isEmpty()) {
            log.error("Fant ingen perioder fra Pesys for bruker med forventet regulert fradrag. Se sikkerlogg for detaljer.")
            sikkerLogg.error("Fant ingen perioder fra Pesys for bruker med forventet regulert fradrag. Bruker=$fnr")
            return FeilMedRegulertFradrag.IngenPeriodeFraPesys.left()
        }
        val pesysPeriode = forventetPesysPeriode.single()
        if (pesysPeriode.perioder.size != 2) {
            return FeilMedRegulertFradrag.ManglerPeriodeFørOgEtterReguleringFraPesys.left()
        }

        val førRegulering = pesysPeriode.perioder[0]
        val forventetGammelG = satsFactory.grunnbeløp(månedFørRegulering).grunnbeløpPerÅr
        if (førRegulering.grunnbelop != forventetGammelG) {
            return FeilMedRegulertFradrag.GrunnbeløpFraPesysUliktForventetGammelt.left()
        }

        val etterRegulering = pesysPeriode.perioder[1]
        val forventetNyG = satsFactory.grunnbeløp(månedFørRegulering.plusMonths(1)).grunnbeløpPerÅr
        if (etterRegulering.grunnbelop != forventetNyG) {
            return FeilMedRegulertFradrag.GrunnbeløpFraPesysUliktForventetNytt.left()
        }

        if (fradrag.fradragstype != Fradragstype.ForventetInntekt) {
            return RegulertFradragEksternKilde(
                fnr = fnr,
                førRegulering = pesysPeriode.perioder[0].netto,
                etterRegulering = pesysPeriode.perioder[1].netto,
            ).right()
        } else {
            val inntektEtterUføreFørRegulering =
                (pesysPeriode.perioder[0] as UføreBeregningsperiode).oppjustertInntektEtterUfore
            val inntektEtterUføreEtterRegulering =
                (pesysPeriode.perioder[1] as UføreBeregningsperiode).oppjustertInntektEtterUfore

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
            )
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
