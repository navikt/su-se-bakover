package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.LocalDate
import kotlin.collections.map

/**
 * Parameter-objekt for å hente regulerte beløp som har blitt brukt som fradrag.
 * Basert på reguleringsmåned og en liste saker utledes alle brukere og eps'er som
 * har fradrag som har blitt regulert eksternt.
 *
 * @property månedFørRegulering Måneden før reguleringen skal gjelde (LocalDate representerer første dag i måneden).
 *           Brukes for å hente perioder før og etter reguleringen.
 * @property brukereMedEps Liste over brukere med tilhørende ektefelle/partner (EPS) som skal hentes regulerte beløp for.
 *           Hver bruker har informasjon om fradragstype som skal hentes.
 */
data class HentReguleringerPesysParameter(
    val månedFørRegulering: LocalDate,
    val brukereMedEps: List<BrukerMedEps>,
) {

    /**
     * Representerer en bruker med eventuell ektefelle/partner (EPS) og deres fradrag.
     *
     * @property fnr Fødselsnummer til bruker
     * @property sakstype Type sak (Alder, Uføre)
     * @property fradragstyperBruker Relevante eksterne fradragstyper for bruker
     * @property eps Fødselsnummer til ektefelle/partner/samboer, eller null hvis bruker ikke har EPS
     * @property fradragstyperEps Relevante eksterne fradragstyper for EPS
     */
    data class BrukerMedEps(
        val fnr: Fnr,
        val sakstype: Sakstype,
        val fradragstyperBruker: Set<Fradragstype>,

        val eps: Fnr?,
        val fradragstyperEps: Set<Fradragstype>,
    )

    companion object {
        fun utledGrunnlagFraSaker(
            reguleringsMåned: Måned,
            forSaker: List<SakTilRegulering>,
        ): HentReguleringerPesysParameter {
            return HentReguleringerPesysParameter(
                månedFørRegulering = reguleringsMåned.fraOgMed.minusMonths(1),
                brukereMedEps = forSaker.map { it.toBrukerMedEps(reguleringsMåned) },
            )
        }

        private fun SakTilRegulering.toBrukerMedEps(reguleringsMåned: Måned): BrukerMedEps {
            val relevanteEksterneFradrag = listOf(
                Fradragstype.Uføretrygd,
                Fradragstype.Alderspensjon,
                Fradragstype.Arbeidsavklaringspenger,
            )
            val grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata
            return BrukerMedEps(
                fnr = sakInfo.fnr,
                sakstype = sakInfo.type,
                fradragstyperBruker = grunnlagsdata.hentBrukteFradragstyperBasertPåKunNorske(
                    fradragstyper = relevanteEksterneFradrag,
                    måned = reguleringsMåned,
                    tilhører = FradragTilhører.BRUKER,
                ).toSet(),
                eps = grunnlagsdata.epsForMåned()[reguleringsMåned],
                fradragstyperEps = grunnlagsdata.hentBrukteFradragstyperBasertPåKunNorske(
                    fradragstyper = relevanteEksterneFradrag,
                    måned = reguleringsMåned,
                    tilhører = FradragTilhører.EPS,
                ).toSet(),
            )
        }
    }
}

data class HentingAvEksterneReguleringerFeiletForBruker(
    val fnr: Fnr,
    val alleFeil: List<FeilMedEksternRegulering>,
)

interface FeilMedEksternRegulering {
    // TODO auto-reg-26  - Denne vil slå ut der bruker er bare ikke er innvilget? Her skal det falle til manuelt..
    // Hadde vært kjekt å se om alle disse faktisk ikke var løpende i Pesys..
    object KunneIkkeHenteFraPesys : FeilMedEksternRegulering
    object IngenPeriodeFraPesys : FeilMedEksternRegulering
    object ManglerPeriodeFørOgEtterReguleringFraPesys : FeilMedEksternRegulering
    object GrunnbeløpFraPesysUliktForventetGammelt : FeilMedEksternRegulering
    object GrunnbeløpFraPesysUliktForventetNytt : FeilMedEksternRegulering
    object OverlappendePeriodeFraPesys : FeilMedEksternRegulering
    object FlerePesysFradragstyperForSammePerson : FeilMedEksternRegulering
    object KunneIkkeHenteAap : FeilMedEksternRegulering
    object IngenGyldigAapPeriode : FeilMedEksternRegulering
    object FlereGyldigeAapPerioder : FeilMedEksternRegulering
    object AapIkkeBekreftetRegulert : FeilMedEksternRegulering
    object AapBeløpErIkkeØkning : FeilMedEksternRegulering
    object AapVedtaksdatoErikkeSammeSomReguleringtidspunkt : FeilMedEksternRegulering
}

class UthentingAvPerioderUføreFeilet : IllegalStateException()
class UthentingAvPerioderAlderFeilet : IllegalStateException()
