package no.nav.su.se.bakover.statistikk.stønad

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.statistikk.StønadsklassifiseringDto
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Data transfer object for stønadsstatistikk (support statistics).
 * @property harUtenlandsOpphold Er dvh sin AVVIK_UTL_OPPHOLD Knyttet opp mot vilkårsvurderingen
 * @property harFamiliegjenforening Angir om bruker har kommet pga familiegjenforening
 * @property statistikkAarMaaned År og måned statistikken gjelder for.
 * @property personnummer Personens fødselsnummer.
 * @property personNummerEktefelle Fødselsnummer til ektefelle, hvis aktuelt.
 * @property funksjonellTid Tidspunktet da hendelsen faktisk ble gjennomført eller registrert i kildesystemet.
 * Format: yyyy-MM-dd'T'HH:mm:ss.SSSSSS. Dette er det tidspunktet hendelsen gjelder fra.
 * Ved oppdatering av historiske data, angir dette når endringen offisielt gjelder.
 * @property tekniskTid Tidspunktet da kildesystemet ble klar over hendelsen.
 * Format: yyyy-MM-dd'T'HH:mm:ss.SSSSSS. Brukes til å holde oversikt over når endringer faktisk ble gjort.
 * @property stonadstype Type stønad. For eksempel SU Ufør eller SU Alder.
 * @property sakId Unik nøkkel til saken i kildesystemet. Kan også omtales som fagsak.
 * Identifiserer samlingen av behandlinger som tilhører saken.
 * @property vedtaksdato Dato for når vedtaket ble fattet.
 * @property vedtakstype Type vedtak, for eksempel førstegangssøknad, revurdering eller klage.
 * @property vedtaksresultat Resultatet av vedtaket, for eksempel Innvilget eller Opphørt.
 * @property behandlendeEnhetKode Kode som angir hvilken enhet som behandlet saken på vedtakstidspunktet.
 * @property ytelseVirkningstidspunkt Dato for når ytelsen tredde i kraft første gang.
 * @property gjeldendeStonadVirkningstidspunkt Dato for når gjeldende stønadsperiode startet.
 * @property gjeldendeStonadStopptidspunkt Dato for når gjeldende stønadsperiode avsluttes.
 * @property gjeldendeStonadUtbetalingsstart Dato for når utbetalinger starter i gjeldende periode.
 * @property gjeldendeStonadUtbetalingsstopp Dato for når utbetalinger stoppes i gjeldende periode.
 * @property månedsbeløp Liste over månedlige beløp og tilhørende detaljer.
 * @property opphorsgrunn Grunn for opphør av ytelsen, hvis aktuelt.
 * @property opphorsdato Dato for når ytelsen ble opphørt, hvis aktuelt.
 * @property flyktningsstatus Angir om personen har flyktningstatus. Alle med SU Ufør vil være flyktning.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class StønadstatistikkDto(
    val harUtenlandsOpphold: String? = null,
    val harFamiliegjenforening: Boolean? = null,
    val statistikkAarMaaned: YearMonth,
    val personnummer: Fnr,
    val personNummerEktefelle: Fnr? = null,
    val funksjonellTid: Tidspunkt,
    val tekniskTid: Tidspunkt,
    val stonadstype: Stønadstype,
    val sakId: UUID,
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
    val opphorsgrunn: String? = null,
    val opphorsdato: LocalDate? = null,
    val flyktningsstatus: String?,
    val versjon: String?,
) {
    enum class Stønadstype(val beskrivelse: String) {
        SU_UFØR("SU Ufør"),
        SU_ALDER("SU Alder"),
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

    /**
     * @property måned for når beløpene gjelder, f.eks. Jan 2021
     * @property stonadsklassifisering Klassifisering av hva som gjør at stønadsmottaker mottar ordinær eller høy sats.
     * @property bruttosats Utgangspunktet for månedlig utbetaling, før fradrag blir trukket fra.
     * @property nettosats Faktisk utbetaling per måned.
     * @property fradragSum Summen av alle fradrag/inntekter som gjelder for stønadsmottaker.
     */
    data class Månedsbeløp(
        val måned: String,
        val stonadsklassifisering: StønadsklassifiseringDto,
        val bruttosats: Long,
        val nettosats: Long,
        val inntekter: List<Inntekt>,
        val fradragSum: Long,
    )

    /**
     * @property inntektstype Type inntekt, f.eks. arbeidsinntekt, sosialstønad, osv.
     * @property beløp Inntekten i kroner per måned.
     */
    data class Inntekt(
        val inntektstype: String,
        val beløp: Long,
        val tilhører: String,
        val erUtenlandsk: Boolean,
    )
}
