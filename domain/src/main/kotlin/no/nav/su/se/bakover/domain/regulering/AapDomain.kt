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
 * Vi behandler manglende fom/tom som ugyldig respons for denne logikken i stedet for å gjette at perioden er åpen.
 */
fun MaksimumVedtakDto.gjelderPå(dato: LocalDate): Boolean {
    val periode = periode ?: return false
    val fom = periode.fraOgMedDato ?: return false
    val tom = periode.tilOgMedDato ?: return false

    return !dato.isBefore(fom) && !dato.isAfter(tom)
}

/**
 * Om et vedtak er "aktivt" må avgjøres ut fra AAPs egne felter, ikke bare på periode.
 *
 * Gyldige statuser vi bruker:
 * - KELVIN: bare `LØPENDE` regnes som aktivt vedtak. `AVSLUTTET`, `UTREDES` og andre statuser
 *   regnes ikke som aktiv AAP for oppslagene våre.
 * - ARENA: bare `IVERK` kan være aktivt, og da bare når vedtakstypen ikke er stans.
 *   `AVSLU` og øvrige Arena-statuser regnes ikke som aktiv AAP for oppslagene våre.
 *
 * Kelvin bruker altså status direkte, mens Arena også trenger vedtakstype: et Arena-vedtak med
 * type "S" betyr stans når det er iverksatt, og skal derfor ikke regnes som aktiv AAP selv om
 * perioden overlapper datoen.
 */
fun MaksimumVedtakDto.erAktivtVedtak(): Boolean {
    return when (kildesystem) {
        Kildesystem.KELVIN -> status == AapVedtakStatus.LØPENDE
        // Arena vedtakstype "S" betyr stans når vedtaket er iverksatt, og skal derfor ikke
        // regnes som aktiv AAP i oppslagene våre.
        Kildesystem.ARENA -> status == AapVedtakStatus.IVERK && !vedtaksTypeKode.equals("S", ignoreCase = true)
        null -> false
    }
}

fun MaksimumVedtakDto.erAktivtVedtakPå(dato: LocalDate): Boolean {
    return gjelderPå(dato) && erAktivtVedtak()
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
