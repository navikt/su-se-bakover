package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperioderPerPerson
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysPeriode
import no.nav.su.se.bakover.client.pesys.PesysPerioderForPerson
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperiode
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperioderPerPerson
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.FeilMedEksternRegulering
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter.BrukerMedEps
import no.nav.su.se.bakover.domain.regulering.HentingAvEksterneReguleringerFeiletForBruker
import no.nav.su.se.bakover.domain.regulering.ReguleringerFraPesysService
import no.nav.su.se.bakover.domain.regulering.RegulertBeløp
import no.nav.su.se.bakover.domain.regulering.UthentingAvPerioderAlderFeilet
import no.nav.su.se.bakover.domain.regulering.UthentingAvPerioderUføreFeilet
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.LocalDate
import kotlin.collections.List
import kotlin.collections.flatMap
import kotlin.collections.map

class ReguleringerFraPesysServiceImpl(
    private val pesysClient: PesysClient,
    private val satsFactory: SatsFactory,
) : ReguleringerFraPesysService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentReguleringer(parameter: HentReguleringerPesysParameter): List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>> {
        val (månedFørRegulering, brukereMedEps) = parameter

        val uførePerioder = hentPerioderUføre(brukereMedEps, månedFørRegulering)
        val alderPerioder = hentPerioderAlder(brukereMedEps, månedFørRegulering)

        return utledRegulerteFradragForBrukerMedEps(
            brukereMedEps = brukereMedEps,
            perioderFraPesys = uførePerioder + alderPerioder,
            månedFørRegulering = månedFørRegulering,
        )
    }

    /**
     * Finner innvilget periode før og etter regulering fra Pesys for en angitt bruker og dens eps i periodene.
     * Hver utledet periode blir mappet til ønsket beløp og verifisert at er i riktig tilstand.
     * Se [utledOgVerifiserRegulertBeløp].
     */
    private fun utledRegulerteFradragForBrukerMedEps(
        brukereMedEps: List<BrukerMedEps>,
        perioderFraPesys: List<PesysPerioderForPerson>,
        månedFørRegulering: LocalDate,
    ): List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>> {
        return brukereMedEps.map { brukerMedEps ->
            val reguleringForBruker =
                utledOgVerifiserRegulertBeløp(brukerMedEps.fnr, perioderFraPesys, månedFørRegulering)

            val reguleringForEps = brukerMedEps.eps?.let { epsFnr ->
                utledOgVerifiserRegulertBeløp(epsFnr, perioderFraPesys, månedFørRegulering)
            }

            val regulertIeu = if (brukerMedEps.sakstype == Sakstype.UFØRE) {
                utledInntektEtterUføre(brukerMedEps.fnr, månedFørRegulering, perioderFraPesys)
            } else {
                null
            }

            val feil = listOfNotNull(reguleringForBruker, reguleringForEps, regulertIeu).filterLefts()
            if (feil.isNotEmpty()) {
                HentingAvEksterneReguleringerFeiletForBruker(
                    fnr = brukerMedEps.fnr,
                    alleFeil = feil,
                ).left()
            } else {
                // På dette stadiet vet vi at alle Either-verdiene er Right på grunn av if
                val brukerBeløp = (reguleringForBruker as Either.Right).value
                val epsBeløp = (reguleringForEps as? Either.Right)?.value
                val ieuBeløp = (regulertIeu as? Either.Right)?.value

                // TODO fjerne listOf når/hvis EksterntRegulerteBeløp fjerner lister
                EksterntRegulerteBeløp(
                    beløpBruker = listOf(brukerBeløp),
                    beløpEps = epsBeløp?.let { listOf(it) } ?: emptyList(),
                    inntektEtterUføre = ieuBeløp,
                ).right()
            }
        }
    }

    private fun utledOgVerifiserRegulertBeløp(
        fnr: Fnr,
        perioderFraPesys: List<PesysPerioderForPerson>,
        månedFørRegulering: LocalDate,
    ): Either<FeilMedEksternRegulering, RegulertBeløp> {
        val (førRegulering, etterRegulering) = hentPeriodeFørOgEtterRegulering(
            fnr,
            månedFørRegulering,
            perioderFraPesys,
        ).getOrElse { return it.left() }
        return RegulertBeløp(
            fnr = fnr,
            førRegulering = førRegulering.netto,
            etterRegulering = etterRegulering.netto,
        ).right()
    }

    private fun utledInntektEtterUføre(
        brukerFnr: Fnr,
        månedFørRegulering: LocalDate,
        perioderFraPesys: List<PesysPerioderForPerson>,
    ): Either<FeilMedEksternRegulering, RegulertBeløp>? {
        val (førRegulering, etterRegulering) = hentPeriodeFørOgEtterRegulering(
            brukerFnr,
            månedFørRegulering,
            perioderFraPesys,
        ).getOrElse { return it.left() }

        if (førRegulering !is UføreBeregningsperiode || etterRegulering !is UføreBeregningsperiode) {
            // Dette skai kke kunne skje fordi denne metoden skal kun brukes for bruker som kun har uføreperioder i Pesys
            throw IllegalStateException("Periode er ikke uføretrygd under utledning av inntekt etter uføre")
        }

        val inntektEtterUføreFørRegulering = førRegulering.oppjustertInntektEtterUfore
        val inntektEtterUføreEtterRegulering = etterRegulering.oppjustertInntektEtterUfore
        return if (inntektEtterUføreFørRegulering != null && inntektEtterUføreEtterRegulering != null) {
            RegulertBeløp(
                fnr = brukerFnr,
                førRegulering = inntektEtterUføreFørRegulering,
                etterRegulering = inntektEtterUføreEtterRegulering,
            ).right()
        } else {
            // Mangler IEU hos Pesys betyr det at det er manuelt behandlet og vi ikke får beløpet
            null
        }
    }

    private fun hentPeriodeFørOgEtterRegulering(
        fnr: Fnr,
        månedFørRegulering: LocalDate,
        perioderFraPesys: List<PesysPerioderForPerson>,
    ): Either<FeilMedEksternRegulering, Pair<PesysPeriode, PesysPeriode>> {
        val forventetPesysPeriode = perioderFraPesys.filter { Fnr(it.fnr) == fnr }
        if (forventetPesysPeriode.size > 1) {
            // Dette skal ikke kunne skje da en bruker skal ikke kunne ha uføretrygd og alderspensjon samtidig.
            log.error("To pesysperioder for samme person som ikke skal være mulig. Sikkerlogg for å se fnr")
            sikkerLogg.error("To pesysperioder for samme person som ikke skal være mulig. Bruker=$fnr")
            return FeilMedEksternRegulering.OverlappendePeriodeFraPesys.left()
        }
        if (forventetPesysPeriode.isEmpty()) {
            log.error("Fant ingen perioder fra Pesys for bruker med forventet regulering. Se sikkerlogg for detaljer.")
            sikkerLogg.error("Fant ingen perioder fra Pesys for bruker med forventet regulering. Bruker=$fnr")
            return FeilMedEksternRegulering.IngenPeriodeFraPesys.left()
        }
        val pesysPeriode = forventetPesysPeriode.single() // Verifisert at er single ovenfor
        if (pesysPeriode.perioder.size != 2) {
            return FeilMedEksternRegulering.ManglerPeriodeFørOgEtterReguleringFraPesys.left()
        }

        val førRegulering = pesysPeriode.perioder.first()
        val forventetGammelG = satsFactory.grunnbeløp(månedFørRegulering).grunnbeløpPerÅr
        if (førRegulering.grunnbelop != forventetGammelG) {
            return FeilMedEksternRegulering.GrunnbeløpFraPesysUliktForventetGammelt.left()
        }

        val etterRegulering = pesysPeriode.perioder.last()
        val forventetNyG = satsFactory.grunnbeløp(månedFørRegulering.plusMonths(1)).grunnbeløpPerÅr
        if (etterRegulering.grunnbelop != forventetNyG) {
            return FeilMedEksternRegulering.GrunnbeløpFraPesysUliktForventetNytt.left()
        }
        return Pair(førRegulering, etterRegulering).right()
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

    // TODO bjg tester må teste denne grundig
    private fun List<BrukerMedEps>.unikeFnrSomBenytterFradragstype(fradragstype: Fradragstype): List<Fnr> =
        flatMap { brukerMedEps ->
            listOfNotNull(
                brukerMedEps.fradragBruker?.let { fradragBruker -> Pair(brukerMedEps.fnr, fradragBruker) },
                brukerMedEps.fradragEps?.let { fradragEps ->
                    Pair(
                        brukerMedEps.eps
                            ?: throw IllegalStateException("Bruker har har fradrag for eps, men mangler eps"),
                        fradragEps,
                    )
                },
            )
        }.filter { (_, fradrag) -> fradrag == fradragstype }
            .map { (fnr, _) -> fnr }
            .distinct()
}
