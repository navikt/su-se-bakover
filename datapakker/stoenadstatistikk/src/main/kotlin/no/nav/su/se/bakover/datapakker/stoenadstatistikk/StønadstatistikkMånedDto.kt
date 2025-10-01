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

    val årsakStans: String? = null,

    val behandlendeEnhetKode: String,

    val stonadsklassifisering: String?,
    val sats: Long?,
    val utbetales: Long?,
    val fradragSum: Long?,
    val uføregrad: Int?,

    val alderspensjon: Int?,
    val alderspensjonEps: Int?,

    val arbeidsavklaringspenger: Int?,
    val arbeidsavklaringspengerEps: Int?,

    val arbeidsinntekt: Int?,
    val arbeidsinntektEps: Int?,

    val omstillingsstønad: Int?,
    val omstillingsstønadEps: Int?,

    val avtalefestetPensjon: Int?,
    val avtalefestetPensjonEps: Int?,

    val avtalefestetPensjonPrivat: Int?,
    val avtalefestetPensjonPrivatEps: Int?,

    val bidragEtterEkteskapsloven: Int?,
    val bidragEtterEkteskapslovenEps: Int?,

    val dagpenger: Int?,
    val dagpengerEps: Int?,

    val fosterhjemsgodtgjørelse: Int?,
    val fosterhjemsgodtgjørelseEps: Int?,

    val gjenlevendepensjon: Int?,
    val gjenlevendepensjonEps: Int?,

    val introduksjonsstønad: Int?,
    val introduksjonsstønadEps: Int?,

    val kapitalinntekt: Int?,
    val kapitalinntektEps: Int?,

    val kontantstøtte: Int?,
    val kontantstøtteEps: Int?,

    val kvalifiseringsstønad: Int?,
    val kvalifiseringsstønadEps: Int?,

    val navYtelserTilLivsopphold: Int?,
    val navYtelserTilLivsoppholdEps: Int?,

    val offentligPensjon: Int?,
    val offentligPensjonEps: Int?,

    val privatPensjon: Int?,
    val privatPensjonEps: Int?,

    val sosialstønad: Int?,
    val sosialstønadEps: Int?,

    val statensLånekasse: Int?,
    val statensLånekasseEps: Int?,

    val supplerendeStønad: Int?,
    val supplerendeStønadEps: Int?,

    val sykepenger: Int?,
    val sykepengerEps: Int?,

    val tiltakspenger: Int?,
    val tiltakspengerEps: Int?,

    val ventestønad: Int?,
    val ventestønadEps: Int?,

    val uføretrygd: Int?,
    val uføretrygdEps: Int?,

    val forventetInntekt: Int?,
    val forventetInntektEps: Int?,

    val avkortingUtenlandsopphold: Int?,
    val avkortingUtenlandsoppholdEps: Int?,

    val underMinstenivå: Int?,
    val underMinstenivåEps: Int?,

    val annet: Int?,
    val annetEps: Int?,
)

/*
Endrer du rekkefølgen her må det også gjenspeiles i bigquery
Rekkefølge i BQ:
        "id", "maaned", "vedtaksdato", "personnummer", "vedtak_fra_og_med", "vedtak_til_og_med",
        "sak_id", "funksjonell_tid", "teknisk_tid", "stonadstype", "personnummer_eps",
        "vedtakstype", "vedtaksresultat", "opphorsgrunn", "opphorsdato", "behandlende_enhet_kode",
        "har_utenlandsopphold", "har_familiegjenforening", "flyktningsstatus", "arsakStans"
 */
fun List<StønadstatistikkMånedDto>.toCSV(): String {
    return buildString {
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
                    dto.årsakStans.orEmpty(),
                    dto.behandlendeEnhetKode,
                    dto.stonadsklassifisering.orEmpty(),
                    dto.sats?.toString().orEmpty(),
                    dto.utbetales?.toString().orEmpty(),
                    dto.fradragSum?.toString().orEmpty(),
                    dto.uføregrad?.toString().orEmpty(),
                    dto.alderspensjon?.toString().orEmpty(),
                    dto.alderspensjonEps?.toString().orEmpty(),
                    dto.arbeidsavklaringspenger?.toString().orEmpty(),
                    dto.arbeidsavklaringspengerEps?.toString().orEmpty(),
                    dto.arbeidsinntekt?.toString().orEmpty(),
                    dto.arbeidsinntektEps?.toString().orEmpty(),
                    dto.omstillingsstønad?.toString().orEmpty(),
                    dto.omstillingsstønadEps?.toString().orEmpty(),
                    dto.avtalefestetPensjon?.toString().orEmpty(),
                    dto.avtalefestetPensjonEps?.toString().orEmpty(),
                    dto.avtalefestetPensjonPrivat?.toString().orEmpty(),
                    dto.avtalefestetPensjonPrivatEps?.toString().orEmpty(),
                    dto.bidragEtterEkteskapsloven?.toString().orEmpty(),
                    dto.bidragEtterEkteskapslovenEps?.toString().orEmpty(),
                    dto.dagpenger?.toString().orEmpty(),
                    dto.dagpengerEps?.toString().orEmpty(),
                    dto.fosterhjemsgodtgjørelse?.toString().orEmpty(),
                    dto.fosterhjemsgodtgjørelseEps?.toString().orEmpty(),
                    dto.gjenlevendepensjon?.toString().orEmpty(),
                    dto.gjenlevendepensjonEps?.toString().orEmpty(),
                    dto.introduksjonsstønad?.toString().orEmpty(),
                    dto.introduksjonsstønadEps?.toString().orEmpty(),
                    dto.kapitalinntekt?.toString().orEmpty(),
                    dto.kapitalinntektEps?.toString().orEmpty(),
                    dto.kontantstøtte?.toString().orEmpty(),
                    dto.kontantstøtteEps?.toString().orEmpty(),
                    dto.kvalifiseringsstønad?.toString().orEmpty(),
                    dto.kvalifiseringsstønadEps?.toString().orEmpty(),
                    dto.navYtelserTilLivsopphold?.toString().orEmpty(),
                    dto.navYtelserTilLivsoppholdEps?.toString().orEmpty(),
                    dto.offentligPensjon?.toString().orEmpty(),
                    dto.offentligPensjonEps?.toString().orEmpty(),
                    dto.privatPensjon?.toString().orEmpty(),
                    dto.privatPensjonEps?.toString().orEmpty(),
                    dto.sosialstønad?.toString().orEmpty(),
                    dto.sosialstønadEps?.toString().orEmpty(),
                    dto.statensLånekasse?.toString().orEmpty(),
                    dto.statensLånekasseEps?.toString().orEmpty(),
                    dto.supplerendeStønad?.toString().orEmpty(),
                    dto.supplerendeStønadEps?.toString().orEmpty(),
                    dto.sykepenger?.toString().orEmpty(),
                    dto.sykepengerEps?.toString().orEmpty(),
                    dto.tiltakspenger?.toString().orEmpty(),
                    dto.tiltakspengerEps?.toString().orEmpty(),
                    dto.ventestønad?.toString().orEmpty(),
                    dto.ventestønadEps?.toString().orEmpty(),
                    dto.uføretrygd?.toString().orEmpty(),
                    dto.uføretrygdEps?.toString().orEmpty(),
                    dto.forventetInntekt?.toString().orEmpty(),
                    dto.forventetInntektEps?.toString().orEmpty(),
                    dto.avkortingUtenlandsopphold?.toString().orEmpty(),
                    dto.avkortingUtenlandsoppholdEps?.toString().orEmpty(),
                    dto.underMinstenivå?.toString().orEmpty(),
                    dto.underMinstenivåEps?.toString().orEmpty(),
                    dto.annet?.toString().orEmpty(),
                    dto.annetEps?.toString().orEmpty(),
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
