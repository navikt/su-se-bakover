package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.person.Fnr
import vilkår.inntekt.domain.grunnlag.FradragTilhører
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
 * @property fradragSomMåRevurderes Fradrag vi ikke klarte å regulere automatisk og som derfor må
 *           håndteres med en revurdering. Brukes f.eks. når AAP ikke har en gyldig periode på
 *           reguleringstidspunktet (kun stans eller opphørt). Markøren gjør at saken havner til
 *           revurdering (utfall MÅ_REVURDERE) i stedet for å feile.
 */
data class EksterntRegulerteBeløp(
    val brukerFnr: Fnr,
    val beløpBruker: List<RegulertBeløp>,
    val beløpEps: List<RegulertBeløp>,
    val inntektEtterUføre: RegulertBeløp? = null,
    val fradragSomMåRevurderes: List<FradragSomMåRevurderes> = emptyList(),
)

/**
 * Markerer at et eksternt fradrag for en gitt person ikke kunne reguleres automatisk og at saken
 * derfor må håndteres med en revurdering. I dag er det kun AAP som markeres på denne måten, men vi
 * bærer med oss fradragstype i tillegg til hvem fradraget tilhører (bruker/EPS) for sporbarhet.
 */
data class FradragSomMåRevurderes(
    val fradragstype: EksterntBeløpSomFradragstype,
    val tilhører: FradragTilhører,
    val feilkode: String,
)

/**
 * Representerer et regulert beløp for en person, med beløp før og etter regulering.
 *
 * @property førRegulering Beløpet før regulering. Kan være null dersom vi ikke har en periode før reguleringen
 *           (typisk når bruker er innvilget i ekstern kilde fra og med reguleringsmåneden).
 * @property etterRegulering Beløpet etter regulering. Det er en invariant at dette beløpet alltid er beregnet
 *           med nytt grunnbeløp — dette valideres ved konstruksjon av [RegulertBeløp]
 *           (se `ReguleringerFraPesysServiceImpl.finnRegulertPesysVedtak` for Pesys og tilsvarende for AAP).
 */
data class RegulertBeløp(
    val fnr: Fnr,
    val fradragstype: EksterntBeløpSomFradragstype,
    val førRegulering: BigDecimal?,
    val etterRegulering: BigDecimal,

    val perioder: List<EksternPeriode> = emptyList(),
    val grunnlagAap: AapGrunnlag? = null,
)

enum class EksterntBeløpSomFradragstype {
    Alderspensjon,
    Arbeidsavklaringspenger,
    Uføretrygd,
    ForventetInntekt,
    ;

    companion object {
        fun from(fradragstype: Fradragstype): EksterntBeløpSomFradragstype = fromOrNull(fradragstype)
            ?: throw IllegalArgumentException("Fradragstype $fradragstype kan ikke brukes som eksternt beløp")

        fun fromOrNull(fradragstype: Fradragstype): EksterntBeløpSomFradragstype? = when (fradragstype) {
            Fradragstype.Alderspensjon -> Alderspensjon
            Fradragstype.Arbeidsavklaringspenger -> Arbeidsavklaringspenger
            Fradragstype.Uføretrygd -> Uføretrygd
            else -> null
        }
    }
}

data class AapGrunnlag(
    val aapFoer: BeregnAap.AapBeregning,
    val aapEtter: BeregnAap.AapBeregning,
)

/**
 * Rådata-periode fra eksternt system (Pesys). Brukes til lagring for etterpå-analyse
 * av reguleringskjøring.
 */
data class EksternPeriode(
    val fom: LocalDate,
    val tom: LocalDate?,
    val grunnbeløp: Int,
    val netto: Int,
    val inntektEtterUføre: Int? = null,
)
