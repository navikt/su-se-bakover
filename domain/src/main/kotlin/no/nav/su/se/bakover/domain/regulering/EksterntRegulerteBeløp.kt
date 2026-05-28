package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.person.Fnr
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Representerer eksternt regulerte beløp hentet fra eksternt system (f.eks. Pesys eller Kelvin).
 * Inneholder regulerte beløp for bruker og eventuelt ektefelle/partner (EPS),
 *
 * @property brukerFnr Fødselsnummer til brukeren/saken dette oppslagsresultatet tilhører.
 * @property beløpBruker regulert beløp for bruker
 * @property beløpEps regulert beløp for ektefelle/partner (EPS)
 * @property inntektEtterUføre Regulert beløp for inntekt etter uføre.
 *           Skal alltid være satt for uføre (aldri AP) med et unntak:
 *           Det eneste tilfelle hvor denne er null for uføre er når inntekt etter uføre er behandlet manuelt i Pesys.
 *           Da vil vi ikke får beløpet fra Pesys og det må behandles manuelt i SU-App også.
 */
data class EksterntRegulerteBeløp(
    val brukerFnr: Fnr,
    val beløpBruker: List<RegulertBeløp>,
    val beløpEps: List<RegulertBeløp>,
    val inntektEtterUføre: RegulertBeløp? = null,
)

/**
 * Representerer et regulert beløp for en person, med beløp før og etter regulering.
 *
 * @property førRegulering Beløpet før regulering. Kan være null dersom vi ikke har en periode før reguleringen
 *           (typisk når bruker er innvilget i ekstern kilde fra og med reguleringsmåneden).
 * @property etterRegulering Beløpet etter regulering
 * @property etterReguleringFraOgMed Fra-og-med-dato for det regulerte beløpet i ekstern kilde.
 *           Brukes til å avgjøre om vårt eksisterende grunnlag i SU-app ble fastsatt før eller etter
 *           ekstern regulering, for å støtte at saksbehandler allerede har lagt inn regulert beløp manuelt.
 */
data class RegulertBeløp(
    val fnr: Fnr,
    val fradragstype: EksterntBeløpSomFradragstype,
    val førRegulering: BigDecimal?,
    val etterRegulering: BigDecimal,
    val etterReguleringFraOgMed: LocalDate?,

    val grunnlagAap: AapGrunnlag? = null,
)

enum class EksterntBeløpSomFradragstype {
    Alderspensjon,
    Arbeidsavklaringspenger,
    Uføretrygd,
    ForventetInntekt,
    ;

    companion object {
        fun from(fradragstype: Fradragstype): EksterntBeløpSomFradragstype = when (fradragstype) {
            Fradragstype.Alderspensjon -> Alderspensjon
            Fradragstype.Arbeidsavklaringspenger -> Arbeidsavklaringspenger
            Fradragstype.Uføretrygd -> Uføretrygd
            else -> throw IllegalArgumentException("Fradragstype $fradragstype kan ikke brukes som eksternt beløp")
        }
    }
}

data class AapGrunnlag(
    val aapFoer: BeregnAap.AapBeregning,
    val aapEtter: BeregnAap.AapBeregning,
)
