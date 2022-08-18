package no.nav.su.se.bakover.statistikk.stønad

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.statistikk.StønadsklassifiseringDto
import java.time.LocalDate
import java.util.UUID

/**
 * @param funksjonellTid Tidspunktet da hendelsen faktisk ble gjennomført eller registrert i kildesystemet
 * @param tekniskTid Tidspunktet da kildesystemet ble klar over hendelsen
 * @param stonadstype Type stønad. Primært "SU Uføre"
 * @param sakId Nøkkelen til saken i kildesystemet
 * @param aktorId Aktør IDen til primær mottager av ytelsen om denne blir godkjent
 * @param sakstype Type sak
 * @param vedtaksdato Dato for når vedtaket ble fattet
 * @param vedtakstype Type vedtak, dvs. førstegangssøknad, revurdering, klage, osv
 * @param vedtaksresultat Resultatet på vedtaket, f.eks. Innvilget, Opphørt, osv
 * @param behandlendeEnhetKode Kode som angir hvilken enhet som faktisk utfører behandlingen på det gjeldende tidspunktet
 * @param ytelseVirkningstidspunkt Dato for når stønadsmottakers ytelse trådte i kraft første gang
 * @param gjeldendeStonadVirkningstidspunkt Dato for når gjeldende stønadsperiode startes
 * @param gjeldendeStonadStopptidspunkt Dato for når gjeldende stønadsperiode avsluttes
 * @param gjeldendeStonadUtbetalingsstart Dato for når utbetalingene starter for gjeldende stønadsperiode
 * @param gjeldendeStonadUtbetalingsstopp Dato for når utbetalingene stoppes for gjeldende stønadsperiode
 * @param månedsbeløp Liste over utbetalingsinformasjonen for hver enkelt måned
 * @param versjon Angir på hvilken versjon av kildekoden JSON stringen er generert på bakgrunn av
 * @param opphorsgrunn Grunn for opphør av ytelsen
 * @param opphorsdato Dato opphøret trer i kraft
 * @param flyktningsstatus Hvorvidt stønadsmottaker har status som flyktning
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
