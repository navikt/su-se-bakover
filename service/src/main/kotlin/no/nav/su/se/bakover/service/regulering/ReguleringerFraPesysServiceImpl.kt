package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysPeriode
import no.nav.su.se.bakover.client.pesys.PesysPerioderForPerson
import no.nav.su.se.bakover.client.pesys.ResponseDtoAlder
import no.nav.su.se.bakover.client.pesys.ResponseDtoUføre
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperiode
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.regulering.EksterntBeløpSomFradragstype
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.FeilMedEksternRegulering
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter.BrukerMedEps
import no.nav.su.se.bakover.domain.regulering.HentingAvEksterneReguleringerFeiletForBruker
import no.nav.su.se.bakover.domain.regulering.RegulertBeløp
import no.nav.su.se.bakover.domain.regulering.UthentingAvPerioderAlderFeilet
import no.nav.su.se.bakover.domain.regulering.UthentingAvPerioderUføreFeilet
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.collections.List
import kotlin.collections.flatMap
import kotlin.collections.map

interface ReguleringerFraPesysService {
    fun hentReguleringer(parameter: HentReguleringerPesysParameter, satsFactory: SatsFactory): List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>>
}

class ReguleringerFraPesysServiceImpl(
    private val pesysClient: PesysClient,
) : ReguleringerFraPesysService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentReguleringer(parameter: HentReguleringerPesysParameter, satsFactory: SatsFactory): List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>> {
        val (månedFørRegulering, brukereMedEps) = parameter

        val uføreRespons = hentPerioderUføre(brukereMedEps, månedFørRegulering)
        val alderRespons = hentPerioderAlder(brukereMedEps, månedFørRegulering)

        return utledRegulerteFradragForBrukerMedEps(
            brukereMedEps = brukereMedEps,
            perioderFraPesys = uføreRespons.resultat + alderRespons.resultat,
            månedFørRegulering = månedFørRegulering,
            feilendeFnr = uføreRespons.feilendeFnr + alderRespons.feilendeFnr,
            satsFactory = satsFactory,
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
        feilendeFnr: List<String> = emptyList(),
        satsFactory: SatsFactory,
    ): List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>> {
        return brukereMedEps.map { brukerMedEps ->
            val fradragstypeBrukerFraPesys = brukerMedEps.fradragstypeBrukerFraPesys()
            val reguleringForBruker =
                fradragstypeBrukerFraPesys.høyreVerdi()?.let {
                    utledOgVerifiserRegulertBeløp(
                        fnr = brukerMedEps.fnr,
                        fradragstype = it,
                        perioderFraPesys = perioderFraPesys,
                        månedFørRegulering = månedFørRegulering,
                        satsFactory = satsFactory,
                    )
                }

            val epsFnr = brukerMedEps.eps
            val fradragstypeEpsFraPesys = brukerMedEps.fradragstypeEpsFraPesys()
            val reguleringForEps = if (epsFnr != null && fradragstypeEpsFraPesys.høyreVerdi() != null) {
                utledOgVerifiserRegulertBeløp(
                    fnr = epsFnr,
                    fradragstype = fradragstypeEpsFraPesys.høyreVerdi()!!,
                    perioderFraPesys = perioderFraPesys,
                    månedFørRegulering = månedFørRegulering,
                    satsFactory = satsFactory,
                )
            } else {
                null
            }

            val regulertIeu = if (
                brukerMedEps.sakstype == Sakstype.UFØRE &&
                fradragstypeBrukerFraPesys.venstreVerdi() == null &&
                fradragstypeBrukerFraPesys.høyreVerdi() != null
            ) {
                utledInntektEtterUføre(brukerMedEps.fnr, månedFørRegulering, perioderFraPesys, satsFactory)
            } else {
                null
            }

            val feiledeOppslag = listOfNotNull(
                FeilMedEksternRegulering.KunneIkkeHenteFraPesys.takeIf { feilendeFnr.contains(brukerMedEps.fnr.toString()) },
                FeilMedEksternRegulering.KunneIkkeHenteFraPesys.takeIf { epsFnr?.toString()?.let(feilendeFnr::contains) == true },
            )

            val feil = feiledeOppslag + listOfNotNull(
                fradragstypeBrukerFraPesys.venstreVerdi(),
                fradragstypeEpsFraPesys.venstreVerdi(),
            ) + listOfNotNull(reguleringForBruker, reguleringForEps, regulertIeu).filterLefts()
            if (feil.isNotEmpty()) {
                HentingAvEksterneReguleringerFeiletForBruker(
                    fnr = brukerMedEps.fnr,
                    alleFeil = feil,
                ).left()
            } else {
                EksterntRegulerteBeløp(
                    brukerFnr = brukerMedEps.fnr,
                    beløpBruker = listOfNotNull(reguleringForBruker.høyreVerdi()),
                    beløpEps = listOfNotNull(reguleringForEps.høyreVerdi()),
                    inntektEtterUføre = regulertIeu.høyreVerdi(),
                ).right()
            }
        }
    }

    private fun utledOgVerifiserRegulertBeløp(
        fnr: Fnr,
        fradragstype: Fradragstype,
        perioderFraPesys: List<PesysPerioderForPerson>,
        månedFørRegulering: LocalDate,
        satsFactory: SatsFactory,
    ): Either<FeilMedEksternRegulering, RegulertBeløp> {
        val (førRegulering, etterRegulering) = finnRegulertPesysVedtak(
            fnr,
            månedFørRegulering,
            perioderFraPesys,
            satsFactory,
        ).getOrElse { return it.left() }
        return RegulertBeløp(
            fnr = fnr,
            fradragstype = EksterntBeløpSomFradragstype.from(fradragstype),
            førRegulering = førRegulering?.let { BigDecimal.valueOf(it.netto.toLong()).setScale(2) },
            etterRegulering = BigDecimal.valueOf(etterRegulering.netto.toLong()).setScale(2),
        ).right()
    }

    private fun utledInntektEtterUføre(
        brukerFnr: Fnr,
        månedFørRegulering: LocalDate,
        perioderFraPesys: List<PesysPerioderForPerson>,
        satsFactory: SatsFactory,
    ): Either<FeilMedEksternRegulering, RegulertBeløp>? {
        val (førRegulering, etterRegulering) = finnRegulertPesysVedtak(
            brukerFnr,
            månedFørRegulering,
            perioderFraPesys,
            satsFactory,
        ).getOrElse { return it.left() }

        if (etterRegulering !is UføreBeregningsperiode || (førRegulering != null && førRegulering !is UføreBeregningsperiode)) {
            // Dette skal ikke kunne skje fordi denne metoden skal kun brukes for bruker som kun har uføreperioder i Pesys
            throw IllegalStateException("Periode er ikke uføretrygd under utledning av inntekt etter uføre")
        }

        val inntektEtterUføreFørRegulering = (førRegulering as UføreBeregningsperiode?)?.oppjustertInntektEtterUfore
        val inntektEtterUføreEtterRegulering = etterRegulering.oppjustertInntektEtterUfore
        return if (inntektEtterUføreEtterRegulering != null) {
            RegulertBeløp(
                fnr = brukerFnr,
                fradragstype = EksterntBeløpSomFradragstype.ForventetInntekt,
                førRegulering = inntektEtterUføreFørRegulering?.let { BigDecimal.valueOf(it.toLong()).setScale(2) },
                etterRegulering = BigDecimal.valueOf(inntektEtterUføreEtterRegulering.toLong()).setScale(2),
            ).right()
        } else {
            // Mangler IEU hos Pesys betyr det at det er manuelt behandlet og vi ikke får beløpet
            null
        }
    }

    /**
     * Finner Pesys-perioden som dekker reguleringsmåneden (etterRegulering) og verifiserer at den har
     * forventet nytt grunnbeløp.
     *
     * Returnerer i tillegg perioden som dekker måneden før reguleringen (førRegulering) hvis den finnes,
     * og verifiserer da at den har forventet gammelt grunnbeløp. Hvis bruker er innvilget i Pesys fra og med
     * reguleringsmåneden vil førRegulering være null — dette er en gyldig tilstand.
     */
    private fun finnRegulertPesysVedtak(
        fnr: Fnr,
        månedFørRegulering: LocalDate,
        perioderFraPesys: List<PesysPerioderForPerson>,
        satsFactory: SatsFactory,
    ): Either<FeilMedEksternRegulering, Pair<PesysPeriode?, PesysPeriode>> {
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
        val pesysPeriode = forventetPesysPeriode.single()
        if (pesysPeriode.perioder.isEmpty()) {
            return FeilMedEksternRegulering.IngenPeriodeFraPesys.left()
        }

        val reguleringsMåned = månedFørRegulering.plusMonths(1)

        val etterRegulering = pesysPeriode.perioder.dekker(reguleringsMåned, fnr).getOrElse { return it.left() }
            ?: return FeilMedEksternRegulering.FantIkkePesysVedtakForReguleringsmåned.left()

        val forventetNyG = satsFactory.grunnbeløp(reguleringsMåned).grunnbeløpPerÅr
        if (etterRegulering.grunnbelop != forventetNyG) {
            return FeilMedEksternRegulering.GrunnbeløpFraPesysUliktForventetNytt.left()
        }

        val førRegulering = pesysPeriode.perioder.dekker(månedFørRegulering, fnr).getOrElse { return it.left() }
        if (førRegulering != null) {
            val forventetGammelG = satsFactory.grunnbeløp(månedFørRegulering).grunnbeløpPerÅr
            if (førRegulering.grunnbelop != forventetGammelG) {
                return FeilMedEksternRegulering.GrunnbeløpFraPesysUliktForventetGammelt.left()
            }
        }

        return Pair(førRegulering, etterRegulering).right()
    }

    /**
     * Pesys-perioder for én bruker skal være ikke-overlappende. Hvis vi likevel får flere treff på
     * samme dato tolker vi det som en datakvalitetsfeil og avbryter — vi vil ikke vilkårlig velge
     * én og risikere å regulere mot feil grunnbeløp. Saken må da behandles manuelt.
     */
    private fun List<PesysPeriode>.dekker(
        dato: LocalDate,
        fnr: Fnr,
    ): Either<FeilMedEksternRegulering, PesysPeriode?> {
        val treff = filter { it.periode().overlapper(PeriodeMedOptionalTilOgMed(dato, dato)) }
        return when (treff.size) {
            0 -> Either.Right(null)
            1 -> Either.Right(treff.single())
            else -> {
                log.error("Flere overlappende Pesys-perioder dekker samme dato. Se sikkerlogg for detaljer.")
                sikkerLogg.error("Flere overlappende Pesys-perioder dekker $dato. Bruker=$fnr, antall=${treff.size}")
                FeilMedEksternRegulering.OverlappendePerioderInnenforPesysPeriode.left()
            }
        }
    }

    private fun PesysPeriode.periode() =
        PeriodeMedOptionalTilOgMed(fom, tom)

    private fun hentPerioderUføre(
        brukereMedEps: List<BrukerMedEps>,
        månedFørRegulering: LocalDate,
    ): ResponseDtoUføre {
        val fnrForFradragstype = brukereMedEps.fnrSomBenytterFradragstype(Fradragstype.Uføretrygd)
        // Vi trenger perioder for uføre som får 0 utbetalt i Pesys for Inntekt Etter Uføre
        val uføreBrukere = brukereMedEps.filter { it.sakstype == Sakstype.UFØRE }.map { it.fnr }
        val unikeFnr = (fnrForFradragstype + uføreBrukere).distinct()
        return pesysClient.hentVedtakForPersonPaaDatoUføre(
            fnrList = unikeFnr,
            dato = månedFørRegulering,
        ).getOrElse {
            throw UthentingAvPerioderUføreFeilet()
        }
    }

    private fun hentPerioderAlder(
        brukereMedEps: List<BrukerMedEps>,
        månedFørRegulering: LocalDate,
    ): ResponseDtoAlder {
        val unikeFnr = brukereMedEps.fnrSomBenytterFradragstype(Fradragstype.Alderspensjon).distinct()
        return pesysClient.hentVedtakForPersonPaaDatoAlder(
            fnrList = unikeFnr,
            dato = månedFørRegulering,
        ).getOrElse {
            throw UthentingAvPerioderAlderFeilet()
        }
    }

    // TODO bjg tester må teste denne grundig
    private fun List<BrukerMedEps>.fnrSomBenytterFradragstype(fradragstype: Fradragstype): List<Fnr> =
        flatMap { brukerMedEps ->
            listOfNotNull(
                if (fradragstype in brukerMedEps.fradragstyperBruker) brukerMedEps.fnr else null,
                if (fradragstype in brukerMedEps.fradragstyperEps) {
                    brukerMedEps.eps
                        ?: throw IllegalStateException("Bruker har fradrag for eps, men mangler eps")
                } else {
                    null
                },
            )
        }

    private fun BrukerMedEps.fradragstypeBrukerFraPesys(): Either<FeilMedEksternRegulering, Fradragstype?> =
        utledPesysFradragstype(fradragstyperBruker)

    private fun BrukerMedEps.fradragstypeEpsFraPesys(): Either<FeilMedEksternRegulering, Fradragstype?> =
        utledPesysFradragstype(fradragstyperEps)

    private fun utledPesysFradragstype(fradragstyper: Set<Fradragstype>): Either<FeilMedEksternRegulering, Fradragstype?> {
        val pesysFradrag = fradragstyper.filter { it.erPesysFradrag() }
        return when (pesysFradrag.size) {
            0 -> null.right()
            1 -> pesysFradrag.single().right()
            else -> {
                log.warn("Fant flere Pesys-fradragstyper for samme person i samme måned. Se sikkerlogg for detaljer.")
                sikkerLogg.warn("Fant flere Pesys-fradragstyper for samme person i samme måned. Fradrag=$pesysFradrag")
                FeilMedEksternRegulering.FlerePesysFradragstyperForSammePerson.left()
            }
        }
    }

    private fun Fradragstype.erPesysFradrag(): Boolean =
        this == Fradragstype.Uføretrygd || this == Fradragstype.Alderspensjon

    private fun <L, R> Either<L, R>?.høyreVerdi(): R? = when (this) {
        is Either.Right -> value
        else -> null
    }

    private fun <L, R> Either<L, R>?.venstreVerdi(): L? = when (this) {
        is Either.Left -> value
        else -> null
    }
}
