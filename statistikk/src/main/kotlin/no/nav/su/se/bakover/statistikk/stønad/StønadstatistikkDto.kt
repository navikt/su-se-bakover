package no.nav.su.se.bakover.statistikk.stønad

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.statistikk.StønadsklassifiseringDto
import java.time.LocalDate
import java.util.UUID

/**
 * Se `src/resources/stonad_schema.json` for dokumentasjon.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class StønadstatistikkDto(
    val funksjonellTid: Tidspunkt,
    val tekniskTid: Tidspunkt,
    val stonadstype: Stønadstype,
    val sakId: UUID,
    val aktorId: Long,
    val sakstype: Vedtakstype, // TODO: Skulle denne være noe annet enn en duplikat av vedtakstype?
    val vedtaksdato: LocalDate,
    val vedtakstype: Vedtakstype,
    val vedtaksresultat: Vedtaksresultat,
    val behandlendeEnhetKode: String,
    val ytelseVirkningstidspunkt: LocalDate,
    val gjeldendeStonadVirkningstidspunkt: LocalDate,
    val gjeldendeStonadStopptidspunkt: LocalDate,
    val gjeldendeStonadUtbetalingsstart: LocalDate,
    val gjeldendeStonadUtbetalingsstopp: LocalDate,
    val månedsbeløp: List<Månedsbeløp>,
    val versjon: String?,
    val opphorsgrunn: String? = null,
    val opphorsdato: LocalDate? = null,
    val flyktningsstatus: String? = "FLYKTNING", // Alle som gjelder SU Ufør vil være flyktning
) {
    enum class Stønadstype(val beskrivelse: String) {
        SU_UFØR("SU Ufør"),
    }

    enum class Vedtakstype(val beskrivelse: String) {
        SØKNAD("Søknad"),
        REVURDERING("Revurdering"),
        STANS("Stans"),
        GJENOPPTAK("Gjenopptak"),
        REGULERING("Regulering"),
    }

    enum class Vedtaksresultat(val beskrivelse: String) {
        INNVILGET("Innvilget"),
        OPPHØRT("Opphørt"),
        STANSET("Stanset"),
        GJENOPPTATT("Gjenopptatt"),
        REGULERT("Regulert"),
    }

    data class Månedsbeløp(
        val måned: String,
        val stonadsklassifisering: StønadsklassifiseringDto,
        val bruttosats: Long,
        val nettosats: Long,
        val inntekter: List<Inntekt>,
        val fradragSum: Long,
    )

    data class Inntekt(
        val inntektstype: String,
        val beløp: Long,
        val tilhører: String,
        val erUtenlandsk: Boolean,
    )
}
