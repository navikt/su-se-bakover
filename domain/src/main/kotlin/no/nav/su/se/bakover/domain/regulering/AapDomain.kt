package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertBeregning
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertGrunnlag
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

sealed class BeregnAap : RegelspesifisertBeregning {
    abstract val sats: BigDecimal

    data class AapBeregning(
        override val benyttetRegel: Regelspesifisering,
        override val sats: BigDecimal,
    ) : BeregnAap() {
        companion object {
            fun fraMaksimumVedtak(vedtak: MaksimumVedtakDto): AapBeregning {
                val sats = vedtak.tilMånedsbeløpForSu()
                return AapBeregning(
                    benyttetRegel = Regelspesifiseringer.REGEL_BEREGN_SATS_AAP_MÅNED.benyttRegelspesifisering(
                        verdi = "Beregnet AAP-sats for måned basert på dagsats ${vedtak.dagsats} + barnetillegg ${vedtak.barnetillegg} og vedtaksdato ${vedtak.vedtaksdato} sats: $sats",
                        avhengigeRegler = listOf(RegelspesifisertGrunnlag.GRUNNLAG_DAGSATS_AAP.benyttGrunnlag(vedtak.dagsats.toString())),
                    ),
                    sats = sats,
                )
            }
        }
    }
}

private val stonadsdagerPerAr = BigDecimal(260)
private val manederPerAr = BigDecimal(12)

fun MaksimumVedtakDto.tilMånedsbeløpForSu(): BigDecimal {
    val totalSatsPerDag = dagsats + barnetillegg
    return BigDecimal(totalSatsPerDag)
        .multiply(stonadsdagerPerAr)
        .divide(manederPerAr, 2, RoundingMode.HALF_UP)
}

/**
 * Resultatdata fra AAP maksimum brukt som inntektsgrunnlag i SU.
 *
 * For G-regulering av supplerende stønad trenger vi bare felt som brukes til å finne
 * riktig vedtaksperiode og regne om dagsats til månedsbeløp.
 */
data class MaksimumVedtakDto(
    val dagsats: Int,
    val barnetillegg: Int,
    val opphorsAarsak: String? = null,
    val periode: MaksimumPeriodeDto? = null,
    val vedtaksdato: LocalDate? = null,
)

data class MaksimumPeriodeDto(
    val fraOgMedDato: LocalDate? = null,
    val tilOgMedDato: LocalDate? = null,
)
