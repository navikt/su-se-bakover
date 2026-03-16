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
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.regulering.FeilMedRegulertFradrag
import no.nav.su.se.bakover.domain.regulering.HentEksterneReguleringerRequest
import no.nav.su.se.bakover.domain.regulering.HentEksterneReguleringerRequest.BrukerMedEps
import no.nav.su.se.bakover.domain.regulering.HentingAvRegulerteFradragFeiletForBruker
import no.nav.su.se.bakover.domain.regulering.ReguleringHentEksterneReguleringerService
import no.nav.su.se.bakover.domain.regulering.RegulertBeløpEksternKilde
import no.nav.su.se.bakover.domain.regulering.RegulerteBeløpForBrukerEksternKilde
import no.nav.su.se.bakover.domain.regulering.UthentingAvPerioderAlderFeilet
import no.nav.su.se.bakover.domain.regulering.UthentingAvPerioderUføreFeilet
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
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

    override fun hentEksterneReguleringer(request: HentEksterneReguleringerRequest): List<Either<HentingAvRegulerteFradragFeiletForBruker, RegulerteBeløpForBrukerEksternKilde>> {
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
    ): List<Either<HentingAvRegulerteFradragFeiletForBruker, RegulerteBeløpForBrukerEksternKilde>> {
        return brukereMedEps.map { brukerMedEps ->
            val fradragFraPesysBruker = brukerMedEps.bruker.fradrag.map {
                utledOgVerifiserRegulertFradrag(
                    brukerMedEps.bruker.fnr,
                    perioderFraPesys = perioderFraPesys,
                    månedFørRegulering = månedFørRegulering,
                )
            }
            val eps = brukerMedEps.eps
            val fradragFraPesysEps = eps?.fradrag?.map {
                utledOgVerifiserRegulertFradrag(
                    eps.fnr,
                    perioderFraPesys = perioderFraPesys,
                    månedFørRegulering = månedFørRegulering,
                )
            }
            val regulertIeu = when (brukerMedEps.sakstype) {
                Sakstype.ALDER -> null
                Sakstype.UFØRE -> utledInntektEtterUføre(brukerMedEps, månedFørRegulering, perioderFraPesys)
            }

            val alleFeil =
                listOfNotNull(fradragFraPesysBruker, fradragFraPesysEps, regulertIeu?.let { listOf(it) }).flatten()
                    .filterLefts()
            if (alleFeil.isNotEmpty()) {
                HentingAvRegulerteFradragFeiletForBruker(
                    fnr = brukerMedEps.bruker.fnr,
                    alleFeil = alleFeil,
                ).left()
            } else {
                RegulerteBeløpForBrukerEksternKilde(
                    fnr = brukerMedEps.bruker.fnr,
                    fradrag = fradragFraPesysBruker.map { it.getOrElse { throw IllegalStateException("$it skal returneres som left før dette stadiet!") } },
                    fradragEps = fradragFraPesysEps?.map { it.getOrElse { throw IllegalStateException("$it skal returneres som left før dette stadiet!") } }
                        ?: emptyList(),
                    inntektEtterUføre = regulertIeu?.getOrElse { throw IllegalStateException("$it skal returneres som left før dette stadiet!") },
                ).right()
            }
        }
    }

    private fun utledOgVerifiserRegulertFradrag(
        fnr: Fnr,
        perioderFraPesys: List<PesysPerioderForPerson>,
        månedFørRegulering: LocalDate,
    ): Either<FeilMedRegulertFradrag, RegulertBeløpEksternKilde> {
        val pesysPeriode =
            hentVerifisertPeriode(fnr, månedFørRegulering, perioderFraPesys).getOrElse { return it.left() }
        return RegulertBeløpEksternKilde(
            fnr = fnr,
            førRegulering = pesysPeriode.perioder[0].netto,
            etterRegulering = pesysPeriode.perioder[1].netto,
        ).right()
    }

    private fun utledInntektEtterUføre(
        brukerMedEps: BrukerMedEps,
        månedFørRegulering: LocalDate,
        perioderFraPesys: List<PesysPerioderForPerson>,
    ): Either<FeilMedRegulertFradrag, RegulertBeløpEksternKilde>? {
        val pesysPeriode =
            hentVerifisertPeriode(
                brukerMedEps.bruker.fnr,
                månedFørRegulering,
                perioderFraPesys,
            ).getOrElse { return it.left() }
        val inntektEtterUføreFørRegulering =
            (pesysPeriode.perioder[0] as UføreBeregningsperiode).oppjustertInntektEtterUfore
        val inntektEtterUføreEtterRegulering =
            (pesysPeriode.perioder[1] as UføreBeregningsperiode).oppjustertInntektEtterUfore

        return if (inntektEtterUføreFørRegulering != null && inntektEtterUføreEtterRegulering != null) {
            RegulertBeløpEksternKilde(
                fnr = brukerMedEps.bruker.fnr,
                førRegulering = inntektEtterUføreFørRegulering,
                etterRegulering = inntektEtterUføreEtterRegulering,
            ).right()
        } else {
            null
        }
    }

    private fun hentVerifisertPeriode(
        fnr: Fnr,
        månedFørRegulering: LocalDate,
        perioderFraPesys: List<PesysPerioderForPerson>,
    ): Either<FeilMedRegulertFradrag, PesysPerioderForPerson> {
        val forventetPesysPeriode = perioderFraPesys.filter { Fnr(it.fnr) == fnr }
        if (forventetPesysPeriode.size > 1) {
            // TODO OBS - må muligens endres hvis AAP skal inn her?
            // Dette skal ikke kunne skje da en bruker skal ikke kunne ha uføretrygd og alderspensjon samtidig.
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
        return pesysPeriode.right()
    }

    private fun hentPerioderUføre(
        brukereMedEps: List<BrukerMedEps>,
        dato: LocalDate,
    ): List<UføreBeregningsperioderPerPerson> {
        val unikeFnr =
            brukereMedEps.unikeFnrSomBenytterFradragstype(Fradragstype.Uføretrygd)
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
        val unikeFnr = brukereMedEps.unikeFnrSomBenytterFradragstype(Fradragstype.Alderspensjon)
        return pesysClient.hentVedtakForPersonPaaDatoAlder(
            fnrList = unikeFnr,
            dato = dato,
        ).getOrElse {
            throw UthentingAvPerioderAlderFeilet()
        }.resultat
    }

    private fun List<BrukerMedEps>.unikeFnrSomBenytterFradragstype(fradragstype: Fradragstype): List<Fnr> =
        flatMap { listOfNotNull(it.bruker, it.eps) }
            .filter { person -> person.fradrag.any { it == fradragstype } }
            .map { it.fnr }
            .distinct()
}
