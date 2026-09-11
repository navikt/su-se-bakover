package no.nav.su.se.bakover.service.regulering.grunnbeløp

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.filterLefts
import no.nav.su.se.bakover.common.domain.extensions.filterRights
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.toMåned
import no.nav.su.se.bakover.domain.regulering.BleIkkeRegulert
import no.nav.su.se.bakover.domain.regulering.EksternKilde
import no.nav.su.se.bakover.domain.regulering.EksternReguleringPerioder
import no.nav.su.se.bakover.domain.regulering.EksternReguleringPerioderRepo
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.HentReguleringerPesysParameter
import no.nav.su.se.bakover.domain.regulering.HentingAvEksterneReguleringerFeiletForBruker
import no.nav.su.se.bakover.domain.regulering.RegulertBeløp
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.service.regulering.AapReguleringerService
import no.nav.su.se.bakover.service.regulering.ReguleringerFraPesysService
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.util.UUID

internal class HentEksterneBeløper(
    private val reguleringerFraPesysService: ReguleringerFraPesysService,
    private val aapReguleringerService: AapReguleringerService,
    private val eksternReguleringPerioderRepo: EksternReguleringPerioderRepo,
    private val satsFactory: SatsFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Henter eksterne regulerte beløp (fra Pesys og AAP) for sakene som kan reguleres.
     *
     * Saker der uthenting av eksterne beløp feiler, markeres med
     * [BleIkkeRegulert.ReguleringFeiletVedKlargjøring.UthentingFradragEksterntFeilet]. Saker
     * uten feil beholdes uendret. De eksterne beløpene hentes med
     * [hentEksterntRegulerteBeløpEllerKastFeil] og periodene lagres via [lagreEksternePerioder].
     *
     * @param saker sakene som skal vurderes, hver med eventuelt utfall fra tidligere steg
     * @param fraOgMedMåned måneden reguleringen gjelder fra og med
     * @param kjøringId identifikator for den overordnede kjøringen
     * @return sakene (oppdatert med eventuelle feil) og de eksterne regulerte beløpene
     */
    fun hent(
        saker: List<Either<BleIkkeRegulert, SakTilRegulering>>,
        fraOgMedMåned: Måned,
        kjøringId: UUID,
    ): Pair<List<Either<BleIkkeRegulert, SakTilRegulering>>, List<EksterntRegulerteBeløp>> {
        val sakerSomKanReguleres = saker.filterRights()
        val eksterntRegulerteBeløperMedOgUtenFeil = if (sakerSomKanReguleres.isEmpty()) {
            emptyList()
        } else {
            hentEksterntRegulerteBeløpEllerKastFeil(
                fraOgMedMåned,
                sakerSomKanReguleres,
                satsFactory,
                kjøringId,
            )
        }

        val feilPåEksterneReguleringer = eksterntRegulerteBeløperMedOgUtenFeil.filterLefts()
        val sakerSomSkalReguleresEllerIkkeMedEksterneReguleringer =
            saker.map {
                it.flatMap { sakTilRegulering ->
                    val feil =
                        feilPåEksterneReguleringer.find { it.fnr == sakTilRegulering.sakInfo.fnr }
                    if (feil != null) {
                        BleIkkeRegulert.ReguleringFeiletVedKlargjøring.UthentingFradragEksterntFeilet(
                            feil,
                            sakTilRegulering.sakInfo.saksnummer,
                        )
                            .left()
                    } else {
                        sakTilRegulering.right()
                    }
                }
            }
        return sakerSomSkalReguleresEllerIkkeMedEksterneReguleringer to eksterntRegulerteBeløperMedOgUtenFeil.filterRights()
    }

    /**
     * Henter eksterne regulerte beløp for sakene som kan reguleres, og kaster feil ved ukjente avvik.
     *
     * Bygger oppslagsgrunnlaget for Pesys, henter reguleringer fra henholdsvis Pesys og AAP,
     * lagrer de eksterne periodene per kilde via [lagreEksternePerioder], og slår resultatene
     * sammen per bruker med [slåSammenEksterneReguleringer].
     *
     * @throws RuntimeException dersom uthentingen eller sammenslåingen feiler (feil per bruker
     *         håndteres av kallet som en [Either]-verdi)
     */
    private fun hentEksterntRegulerteBeløpEllerKastFeil(
        fraOgMedMåned: Måned,
        sakerSomKanReguleres: List<SakTilRegulering>,
        satsFactory: SatsFactory,
        kjøringId: UUID,
    ) =
        Either.catch {
            val eksterntOppslagsgrunnlag = HentReguleringerPesysParameter.utledGrunnlagFraSaker(
                reguleringsMåned = fraOgMedMåned.fraOgMed.toMåned(),
                forSaker = sakerSomKanReguleres,
            )
            val fraPesys = reguleringerFraPesysService.hentReguleringer(eksterntOppslagsgrunnlag, satsFactory)
            val fraAap = aapReguleringerService.hentReguleringer(eksterntOppslagsgrunnlag)
            lagreEksternePerioder(kjøringId, sakerSomKanReguleres, fraPesys, EksternKilde.PESYS)
            lagreEksternePerioder(kjøringId, sakerSomKanReguleres, fraAap, EksternKilde.AAP)

            slåSammenEksterneReguleringer(
                brukereMedEps = eksterntOppslagsgrunnlag.brukereMedEps,
                fraPesys = fraPesys,
                fraAap = fraAap,
            )
        }.getOrElse {
            // TODO AUTO-REG-26 Feile enkelt batch?
            throw it
        }

    /**
     * Lagrer de eksterne reguleringsperiodene fra én kilde for en kjøring.
     *
     * Bygger én [EksternReguleringPerioder]-rad per bruker og EPS, og lagrer også rader med
     * feilkoder for feil per bruker og for fradrag som må revurderes. Ukjent kilde for en
     * feil lagres som [FradragTilhører.BRUKER]. Rader uten gjeldende perioder eller feil
     * hoppes over.
     */
    private fun lagreEksternePerioder(
        kjøringId: UUID,
        sakerSomKanReguleres: List<SakTilRegulering>,
        resultater: List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>>,
        kilde: EksternKilde,
    ) {
        val fnrTilSaksnummer = sakerSomKanReguleres.associate { it.sakInfo.fnr to it.sakInfo.saksnummer }
        val rader = resultater.flatMap { resultat ->
            resultat.fold(
                ifLeft = { feil ->
                    val saksnummer = fnrTilSaksnummer[feil.fnr] ?: return@fold emptyList()
                    val feilkoder = feil.alleFeil.map { it.feilkode }
                    // Vi vet ikke om feilen gjelder bruker eller EPS isolert, så lagrer som BRUKER.
                    listOf(
                        EksternReguleringPerioder(
                            kjøringId = kjøringId,
                            saksnummer = saksnummer,
                            tilhører = FradragTilhører.BRUKER,
                            eksternKilde = kilde,
                            perioder = emptyList(),
                            feilkoder = feilkoder,
                        ),
                    )
                },
                ifRight = { eksterntRegulertBeløp ->
                    val saksnummer = fnrTilSaksnummer[eksterntRegulertBeløp.brukerFnr] ?: return@fold emptyList()
                    val brukerRader = eksterntRegulertBeløp.beløpBruker.mapNotNull {
                        radFor(kjøringId, saksnummer, FradragTilhører.BRUKER, kilde, it)
                    }
                    val epsRader = eksterntRegulertBeløp.beløpEps.mapNotNull {
                        radFor(kjøringId, saksnummer, FradragTilhører.EPS, kilde, it)
                    }
                    // Fradrag som må revurderes (f.eks. AAP uten gyldig periode) gir ingen beløp,
                    // men vi lagrer en rad med feilkode for etterpå-analyse.
                    val revurderingsRader = eksterntRegulertBeløp.fradragSomMåRevurderes.map {
                        EksternReguleringPerioder(
                            kjøringId = kjøringId,
                            saksnummer = saksnummer,
                            tilhører = it.tilhører,
                            eksternKilde = kilde,
                            perioder = emptyList(),
                            feilkoder = listOf(it.feilkode),
                        )
                    }
                    brukerRader + epsRader + revurderingsRader
                },
            )
        }
        log.info(
            "Lagrer eksterne reguleringsperioder: kjøring={} kilde={} antallRader={} medFeil={}",
            kjøringId,
            kilde,
            rader.size,
            rader.count { it.feilkoder.isNotEmpty() },
        )
        eksternReguleringPerioderRepo.lagre(rader)
    }

    /**
     * Bygger en [EksternReguleringPerioder]-rad fra et [RegulertBeløp].
     *
     * @return null dersom beløpet ikke har noen perioder (ingen rad skal lagres)
     */
    private fun radFor(
        kjøringId: UUID,
        saksnummer: Saksnummer,
        tilhører: FradragTilhører,
        kilde: EksternKilde,
        beløp: RegulertBeløp,
    ): EksternReguleringPerioder? {
        if (beløp.perioder.isEmpty()) return null
        return EksternReguleringPerioder(
            kjøringId = kjøringId,
            saksnummer = saksnummer,
            tilhører = tilhører,
            eksternKilde = kilde,
            perioder = beløp.perioder,
            feilkoder = emptyList(),
        )
    }
}

/**
 * Slår sammen eksterne reguleringer fra Pesys og AAP per bruker.
 *
 * Krever at begge kildene har ett resultat for hvert forventede fnr. Dersom en kilde har feil
 * for en bruker, returneres feilen (eller begge feilene slått sammen). Dersom begge lykkes,
 * summeres beløpene med [EksterntRegulerteBeløp.plus].
 *
 * @throws IllegalArgumentException dersom resultatene ikke dekker det forventede settet av fnr
 */
internal fun slåSammenEksterneReguleringer(
    brukereMedEps: List<HentReguleringerPesysParameter.BrukerMedEps>,
    fraPesys: List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>>,
    fraAap: List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>>,
): List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>> {
    val forventedeFnr = brukereMedEps.map { it.fnr }
    val forventedeFnrSet = forventedeFnr.toSet()
    val fraPesysPerBruker = fraPesys.associateBy { it.fold(ifLeft = { it.fnr }, ifRight = { it.brukerFnr }) }
    val fraAapPerBruker = fraAap.associateBy { it.fold(ifLeft = { it.fnr }, ifRight = { it.brukerFnr }) }

    require(fraPesysPerBruker.keys == forventedeFnrSet) {
        "Forventet Pesys-resultater for $forventedeFnrSet, men fikk ${fraPesysPerBruker.keys}"
    }
    require(fraAapPerBruker.keys == forventedeFnrSet) {
        "Forventet AAP-resultater for $forventedeFnrSet, men fikk ${fraAapPerBruker.keys}"
    }

    return forventedeFnr.map { fnr ->
        val pesysResultat = fraPesysPerBruker.getValue(fnr)
        val aapResultat = fraAapPerBruker.getValue(fnr)
        when {
            pesysResultat is Either.Left && aapResultat is Either.Left -> HentingAvEksterneReguleringerFeiletForBruker(
                fnr = fnr,
                alleFeil = pesysResultat.value.alleFeil + aapResultat.value.alleFeil,
            ).left()

            pesysResultat is Either.Left -> pesysResultat
            aapResultat is Either.Left -> aapResultat
            pesysResultat is Either.Right && aapResultat is Either.Right -> (pesysResultat.value + aapResultat.value).right()
            else -> throw IllegalStateException("Ukjent kombinasjon ved sammenslåing av eksterne reguleringer for $fnr")
        }
    }
}

/**
 * Summerer to [EksterntRegulerteBeløp]-verdier for samme bruker.
 *
 * Beløp for bruker og EPS, samt fradrag som må revurderes, legges sammen. Inntekt etter uførhet
 * beholdes fra den ene verdien som har den satt (om noen).
 */
private operator fun EksterntRegulerteBeløp.plus(other: EksterntRegulerteBeløp): EksterntRegulerteBeløp {
    return EksterntRegulerteBeløp(
        brukerFnr = this.brukerFnr,
        beløpBruker = this.beløpBruker + other.beløpBruker,
        beløpEps = this.beløpEps + other.beløpEps,
        inntektEtterUføre = this.inntektEtterUføre ?: other.inntektEtterUføre,
        fradragSomMåRevurderes = this.fradragSomMåRevurderes + other.fradragSomMåRevurderes,
    )
}
