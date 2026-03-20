package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock
import java.time.LocalDate

interface ReguleringerFraPesysService {
    fun hentReguleringer(parameter: HentReguleringerPesysParameter): List<Either<HentingAvEksterneReguleringerFeiletForBruker, EksterntRegulerteBeløp>>
}

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
     * @property fradragBruker Fradragstype for bruker (Uføretrygd eller Alderspensjon), eller null hvis ingen
     * @property eps Fødselsnummer til ektefelle/partner/samboer, eller null hvis bruker ikke har EPS
     * @property fradragEps Fradragstype for EPS (Uføretrygd eller Alderspensjon), eller null hvis ingen
     */
    data class BrukerMedEps(
        val fnr: Fnr,
        val sakstype: Sakstype,
        val fradragBruker: Fradragstype?,
        val harAapBruker: Boolean,

        val eps: Fnr?,
        val fradragEps: Fradragstype?,
        val harAapEps: Boolean,
    )

    companion object {
        fun toParameter(
            reguleringsMåned: Måned,
            forSaker: List<Sak>,
            clock: Clock,
        ): HentReguleringerPesysParameter {
            return HentReguleringerPesysParameter(
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

            val uføreOgAlder = listOf(Fradragstype.Uføretrygd, Fradragstype.Alderspensjon)
            val aap = listOf(Fradragstype.Arbeidsavklaringspenger)
            return BrukerMedEps(
                fnr = fnr,
                sakstype = type,
                fradragBruker = grunnlagsdata.hentBrukteFradragstyperBasertPå(
                    fradragstyper = uføreOgAlder,
                    måned = reguleringsMåned,
                    tilhører = FradragTilhører.BRUKER,
                ).singleOrNull(),
                harAapBruker = grunnlagsdata.hentBrukteFradragstyperBasertPå(
                    fradragstyper = aap,
                    måned = reguleringsMåned,
                    tilhører = FradragTilhører.BRUKER,
                ).isNotEmpty(),
                eps = grunnlagsdata.epsForMåned()[reguleringsMåned],
                fradragEps = grunnlagsdata.hentBrukteFradragstyperBasertPå(
                    fradragstyper = uføreOgAlder,
                    måned = reguleringsMåned,
                    tilhører = FradragTilhører.EPS,
                ).singleOrNull(),
                harAapEps = grunnlagsdata.hentBrukteFradragstyperBasertPå(
                    fradragstyper = aap,
                    måned = reguleringsMåned,
                    tilhører = FradragTilhører.EPS,
                ).isNotEmpty(),
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
    object IngenPeriodeFraPesys : FeilMedEksternRegulering
    object ManglerPeriodeFørOgEtterReguleringFraPesys : FeilMedEksternRegulering
    object GrunnbeløpFraPesysUliktForventetGammelt : FeilMedEksternRegulering
    object GrunnbeløpFraPesysUliktForventetNytt : FeilMedEksternRegulering
    object OverlappendePeriodeFraPesys : FeilMedEksternRegulering
}

class UthentingAvPerioderUføreFeilet : IllegalStateException()
class UthentingAvPerioderAlderFeilet : IllegalStateException()
