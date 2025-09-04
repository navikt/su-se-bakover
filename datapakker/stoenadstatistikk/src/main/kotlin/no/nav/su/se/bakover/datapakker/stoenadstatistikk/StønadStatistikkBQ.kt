package no.nav.su.se.bakover.datapakker.stoenadstatistikk

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Data transfer object for stønadsstatistikk (support statistics).
 * @property harUtenlandsOpphold Er dvh sin AVVIK_UTL_OPPHOLD Knyttet opp mot vilkårsvurderingen
 * @property harFamiliegjenforening Angir om bruker har kommet pga familiegjenforening
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
 * Everything here is a copy of [no.nav.su.se.bakover.datapakker.stoenadstatistikk.StønadstatistikkMånedDto] and
 * [no.nav.su.se.bakover.datapakker.stoenadstatistikk.StønadstatistikkDto]
 */
data class StønadstatistikkMånedDto(
    val id: UUID,
    val måned: YearMonth,
    val funksjonellTid: String,
    val tekniskTid: String,
    val sakId: UUID,
    val stonadstype: String,
    val personnummer: String,
    val personNummerEps: String? = null,
    val vedtaksdato: LocalDate,
    val vedtakstype: String,
    val vedtaksresultat: String,
    val vedtakFraOgMed: LocalDate,
    val vedtakTilOgMed: LocalDate,
    val opphorsgrunn: String? = null,
    val opphorsdato: LocalDate? = null,

    val harUtenlandsOpphold: String?,
    val harFamiliegjenforening: String?,
    val flyktningsstatus: String?,

    val årsakStans: String? = null,

    val behandlendeEnhetKode: String,
    val månedsbeløp: Månedsbeløp?,
)

fun List<StønadstatistikkMånedDto>.toCSV(): String {
    return buildString {
        // Header
        appendLine(
            listOf(
                "id", "maaned", "vedtaksdato", "personnummer", "vedtak_fra_og_med", "vedtak_til_og_med",
                "sak_id", "funksjonell_tid", "teknisk_tid", "stonadstype", "personnummer_eps",
                "vedtakstype", "vedtaksresultat", "opphorsgrunn", "opphorsdato", "behandlende_enhet_kode",
                "har_utenlandsopphold", "har_familiegjenforening", "flyktningsstatus", "arsakStans",
            ).joinToString(","),
        )

        // Rows
        for (dto in this@toCSV) {
            appendLine(
                listOf(
                    dto.id.toString(),
                    dto.måned.toString(),
                    dto.vedtaksdato.toString(),
                    dto.personnummer,
                    dto.vedtakFraOgMed.toString(),
                    dto.vedtakTilOgMed.toString(),
                    dto.sakId.toString(),
                    dto.funksjonellTid,
                    dto.tekniskTid,
                    dto.stonadstype,
                    dto.personNummerEps.orEmpty(),
                    dto.vedtakstype,
                    dto.vedtaksresultat,
                    dto.opphorsgrunn.orEmpty(),
                    dto.opphorsdato?.toString().orEmpty(),
                    dto.behandlendeEnhetKode,
                    dto.harUtenlandsOpphold.orEmpty(),
                    dto.harFamiliegjenforening.orEmpty(),
                    dto.flyktningsstatus.orEmpty(),
                    dto.årsakStans.orEmpty(),
                ).joinToString(",") { escapeCsv(it) },
            )
        }
    }
}

private fun escapeCsv(field: String): String {
    val needsQuotes = field.contains(",") || field.contains("\"") || field.contains("\n")
    val escaped = field.replace("\"", "\"\"")
    return if (needsQuotes) "\"$escaped\"" else escaped
}

/**
 * @property måned for når beløpene gjelder, f.eks. Jan 2021
 * @property stonadsklassifisering Klassifisering av hva som gjør at stønadsmottaker mottar ordinær eller høy sats.
 * @property sats Utgangspunktet for månedlig utbetaling, før fradrag blir trukket fra.
 * @property utbetales Faktisk utbetaling per måned.
 * @property fradragSum Summen av alle fradrag/inntekter som gjelder for stønadsmottaker.
 * @property uføregrad uføregrad til bruker hvis uføre sak
 */
data class Månedsbeløp(
    val manedsbelopId: String,
    val måned: String,
    val stonadsklassifisering: String,
    val sats: Long,
    val utbetales: Long,
    val fradrag: List<Fradrag>,
    val fradragSum: Long,
    val uføregrad: Int?,
)

fun Månedsbeløp.toCSV(stoenad_statistikk_id: UUID): String {
    return buildString {
        appendLine(
            listOf(
                stoenad_statistikk_id.toString(),
                this@toCSV.måned,
                this@toCSV.stonadsklassifisering,
                this@toCSV.sats.toString(),
                this@toCSV.utbetales.toString(),
                this@toCSV.fradragSum.toString(),
                this@toCSV.uføregrad.toString(),
            ).joinToString(",") { escapeCsv(it) },
        )
    }
}

/**
 * @property fradragstype Type inntekt, f.eks. arbeidsinntekt, sosialstønad, osv. så basically [Fradragstype.Kategori]
 * @property beløp Inntekten i kroner per måned.
 * @property tilhører er [FradragTilhører]
 */
data class Fradrag(
    val fradragstype: String,
    val beløp: Long,
    val tilhører: String,
    val erUtenlandsk: Boolean,
)

fun List<Fradrag>.toCSV(manedsbelop_id: String): String {
    return buildString {
        appendLine(
            listOf(
                "manedsbelop_id",
                "fradragstype",
                "belop",
                "tilhorer",
                "er_utenlandsk",
            ).joinToString(","),
        )
        for (dto in this@toCSV) {
            appendLine(
                listOf(
                    manedsbelop_id,
                    dto.fradragstype,
                    dto.beløp.toString(),
                    dto.tilhører,
                    dto.erUtenlandsk.toString(),
                ).joinToString(",") { escapeCsv(it) },
            )
        }
    }
}
