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
    val status: AapVedtakStatus? = null,
    val kildesystem: Kildesystem? = null,
    val vedtaksTypeKode: String? = null,
)

data class MaksimumPeriodeDto(
    val fraOgMedDato: LocalDate? = null,
    val tilOgMedDato: LocalDate? = null,
)

/**
 * AAP returnerer vedtak som en liste av perioder for et intervall, ikke ett ferdig valgt "gjeldende vedtak".
 * Derfor må vi først sjekke at vedtaket faktisk dekker datoen vi vurderer.
 *
 * Vi behandler manglende fom som ugyldig respons for denne logikken i stedet for å gjette at perioden er åpen.
 * manglende tom vet vi at betyr åpen periode.
 */
fun MaksimumVedtakDto.gjelderPå(dato: LocalDate): Boolean {
    val periode = periode ?: return false
    val fom = periode.fraOgMedDato ?: return false
    val tom = periode.tilOgMedDato

    return !dato.isBefore(fom) && (tom == null || !dato.isAfter(tom))
}

fun MaksimumVedtakDto.erAktivtVedtakPå(dato: LocalDate): Boolean {
    // vedtak med kode S er stans som er gyldig vedtak men ikke relevant for reguleringen
    val stans = vedtaksTypeKode.equals("S", ignoreCase = true)
    return gjelderPå(dato) && !stans
}

enum class AapVedtakStatus {
    AVSLU,
    FORDE,
    GODKJ,
    INNST,
    IVERK,
    KONT,
    MOTAT,
    OPPRE,
    REGIS,
    UKJENT,
    OPPRETTET,
    SOKNAD_UNDER_BEHANDLING,
    REVURDERING_UNDER_BEHANDLING,
    FERDIGBEHANDLET,
    UTREDES,
    LØPENDE,
    AVSLUTTET,
}

enum class Kildesystem {
    ARENA,
    KELVIN,
}
