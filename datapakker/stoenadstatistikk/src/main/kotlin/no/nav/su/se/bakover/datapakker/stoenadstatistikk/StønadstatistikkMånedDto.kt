package no.nav.su.se.bakover.datapakker.stoenadstatistikk

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Data transfer object for stønadsstatistikk (support statistics).
 * @property id unik id for måned statistikk til en person
 * @property måned hvilket måned statistikken gjelder
 * @property personnummer Personens fødselsnummer.
 * @property sakId Unik nøkkel til saken i kildesystemet. Kan også omtales som fagsak.
 * Identifiserer samlingen av behandlinger som tilhører saken.
 * @property funksjonellTid Tidspunktet da hendelsen faktisk ble gjennomført eller registrert i kildesystemet.
 * Format: yyyy-MM-dd'T'HH:mm:ss.SSSSSS. Dette er det tidspunktet hendelsen gjelder fra.
 * Ved oppdatering av historiske data, angir dette når endringen offisielt gjelder.
 * @property tekniskTid Tidspunktet da kildesystemet ble klar over hendelsen.
 * Format: yyyy-MM-dd'T'HH:mm:ss.SSSSSS. Brukes til å holde oversikt over når endringer faktisk ble gjort.
 * @property stonadstype Type stønad. For eksempel SU Ufør eller SU Alder.
 * @property personNummerEps Fødselsnummer til ektefelle, hvis aktuelt.
 * @property vedtakFraOgMed dato vedtak gjelder i fra
 * @property vedtakTilOgMed dato vedtak gjelder til
 * @property vedtakstype Type vedtak, for eksempel førstegangssøknad, revurdering eller klage.
 * @property vedtaksresultat Resultatet av vedtaket, for eksempel Innvilget eller Opphørt.
 * @property vedtaksdato Dato for når vedtaket ble fattet.
 * @property opphorsgrunn Grunn for opphør av ytelsen, hvis aktuelt.
 * @property opphorsdato Dato for når ytelsen ble opphørt, hvis aktuelt.
 * @property årsakStans årsak til midlertidig stanset ytelse
 * @property behandlendeEnhetKode Kode som angir hvilken enhet som behandlet saken på vedtakstidspunktet.
 * @property stonadsklassifisering
 * @property sats Bruker sin sats før fradrag
 * @property utbetales beløpet bruker har rett på
 * @property fradragSum totalt fradrag
 * @property uforegrad hvilken prosentandel uføre personen er
 * Alle felter nedenfor er inntekter / fradragstyper
 * @property alderspensjon
 * @property alderspensjonEps
 * @property arbeidsavklaringspenger
 * @property arbeidsavklaringspengerEps
 * @property arbeidsinntekt
 * @property arbeidsinntektEps
 * @property omstillingsstonad
 * @property omstillingsstonadEps
 * @property avtalefestetPensjon
 * @property avtalefestetPensjonEps
 * @property avtalefestetPensjonPrivat
 * @property avtalefestetPensjonPrivatEps
 * @property bidragEtterEkteskapsloven
 * @property bidragEtterEkteskapslovenEps
 * @property dagpenger
 * @property dagpengerEps
 * @property fosterhjemsgodtgjorelse
 * @property fosterhjemsgodtgjorelseEps
 * @property gjenlevendepensjon
 * @property gjenlevendepensjonEps
 * @property introduksjonsstonad
 * @property introduksjonsstonadEps
 * @property kapitalinntekt
 * @property kapitalinntektEps
 * @property kontantstotte
 * @property kontantstotteEpu
 * @property kvalifiseringsstonad
 * @property kvalifiseringsstonadEps
 * @property navYtelserTilLivsopphold
 * @property navYtelserTilLivsoppholdEps
 * @property offentligPensjon
 * @property offentligPensjonEps
 * @property privatPensjon
 * @property privatPensjonEps
 * @property sosialstonad
 * @property sosialstonadEps
 * @property statensLanekasse
 * @property statensLanekasseEps
 * @property supplerendeStonad
 * @property supplerendeStonadEps
 * @property sykepenger
 * @property sykepengerEps
 * @property tiltakspenger
 * @property tiltakspengerEps
 * @property ventestonad
 * @property ventestonadEps
 * @property uforetrygd
 * @property uforetrygdEps
 * @property forventetInntekt
 * @property forventetInntektEps
 * @property avkortingUtenlandsopphold
 * @property avkortingUtenlandsoppholdEps
 * @property underMinstenivå
 * @property underMinstenivåEps
 * @property annet
 * @property annetEps
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
    val uforegrad: Int?,

    val alderspensjon: Int?,
    val alderspensjonEps: Int?,

    val arbeidsavklaringspenger: Int?,
    val arbeidsavklaringspengerEps: Int?,

    val arbeidsinntekt: Int?,
    val arbeidsinntektEps: Int?,

    val omstillingsstonad: Int?,
    val omstillingsstonadEps: Int?,

    val avtalefestetPensjon: Int?,
    val avtalefestetPensjonEps: Int?,

    val avtalefestetPensjonPrivat: Int?,
    val avtalefestetPensjonPrivatEps: Int?,

    val bidragEtterEkteskapsloven: Int?,
    val bidragEtterEkteskapslovenEps: Int?,

    val dagpenger: Int?,
    val dagpengerEps: Int?,

    val fosterhjemsgodtgjorelse: Int?,
    val fosterhjemsgodtgjorelseEps: Int?,

    val gjenlevendepensjon: Int?,
    val gjenlevendepensjonEps: Int?,

    val introduksjonsstonad: Int?,
    val introduksjonsstonadEps: Int?,

    val kapitalinntekt: Int?,
    val kapitalinntektEps: Int?,

    val kontantstotte: Int?,
    val kontantstotteEpu: Int?,

    val kvalifiseringsstonad: Int?,
    val kvalifiseringsstonadEps: Int?,

    val navYtelserTilLivsopphold: Int?,
    val navYtelserTilLivsoppholdEps: Int?,

    val offentligPensjon: Int?,
    val offentligPensjonEps: Int?,

    val privatPensjon: Int?,
    val privatPensjonEps: Int?,

    val sosialstonad: Int?,
    val sosialstonadEps: Int?,

    val statensLanekasse: Int?,
    val statensLanekasseEps: Int?,

    val supplerendeStonad: Int?,
    val supplerendeStonadEps: Int?,

    val sykepenger: Int?,
    val sykepengerEps: Int?,

    val tiltakspenger: Int?,
    val tiltakspengerEps: Int?,

    val ventestonad: Int?,
    val ventestonadEps: Int?,

    val uforetrygd: Int?,
    val uforetrygdEps: Int?,

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
       "id", "maned", "vedtaksdato", "personnummer", "vedtakFraOgMed", "vedtakTilOgMed", "sakId",
       "funksjonellTid", "tekniskTid", "stonadstype", "personnummerEps", "vedtakstype", "vedtaksresultat",
       "opphorsgrunn", "opphorsdato", "arsakStans", "behandlendeEnhetKode", "stonadsklassifisering", "sats",
       "utbetales", "fradragsum", "uforegrad", "alderspensjon", "alderspensjoneps", "arbeidsavklaringspenger",
       "arbeidsavklaringspengereps", "arbeidsinntekt", "arbeidsinntekteps", "omstillingsstonad", "omstillingsstonadeps",
       "avtalefestetpensjon", "avtalefestetpensjoneps", "avtalefestetpensjonprivat", "avtalefestetpensjonprivateps",
       "bidragetterekteskapsloven", "bidragetterekteskapsloveneps", "dagpenger", "dagpengereps",
       "fosterhjemsgodtgjorelse", "fosterhjemsgodtgjorelseeps", "gjenlevendepensjon", "gjenlevendepensjoneps",
       "introduksjonsstonad", "introduksjonsstonadeps", "kapitalinntekt", "kapitalinntekteps", "kontantstotte",
       "kontantstotteeps", "kvalifiseringsstonad", "kvalifiseringsstonadeps", "navytelsertillivsopphold",
       "navytelsertillivsoppholdeps", "offentligpensjon", "offentligpensjoneps", "privatpensjon",
       "privatpensjoneps", "sosialstonad", "sosialstonadeps", "statenslanekasse", "statenslanekasseeps",
       "supplerendestonad", "supplerendestonadeps", "sykepenger", "sykepengereps", "tiltakspenger",
       "tiltakspengereps", "ventestonad", "ventestonadeps", "uforetrygd", "uforetrygdeps", "forventetinntekt",
       "forventetinntekteps", "avkortingutenlandsopphold", "avkortingutenlandsoppholdeps",
       "underminsteniva", "underminstenivaeps", "annet", "anneteps",
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
                    dto.uforegrad?.toString().orEmpty(),
                    dto.alderspensjon?.toString().orEmpty(),
                    dto.alderspensjonEps?.toString().orEmpty(),
                    dto.arbeidsavklaringspenger?.toString().orEmpty(),
                    dto.arbeidsavklaringspengerEps?.toString().orEmpty(),
                    dto.arbeidsinntekt?.toString().orEmpty(),
                    dto.arbeidsinntektEps?.toString().orEmpty(),
                    dto.omstillingsstonad?.toString().orEmpty(),
                    dto.omstillingsstonadEps?.toString().orEmpty(),
                    dto.avtalefestetPensjon?.toString().orEmpty(),
                    dto.avtalefestetPensjonEps?.toString().orEmpty(),
                    dto.avtalefestetPensjonPrivat?.toString().orEmpty(),
                    dto.avtalefestetPensjonPrivatEps?.toString().orEmpty(),
                    dto.bidragEtterEkteskapsloven?.toString().orEmpty(),
                    dto.bidragEtterEkteskapslovenEps?.toString().orEmpty(),
                    dto.dagpenger?.toString().orEmpty(),
                    dto.dagpengerEps?.toString().orEmpty(),
                    dto.fosterhjemsgodtgjorelse?.toString().orEmpty(),
                    dto.fosterhjemsgodtgjorelseEps?.toString().orEmpty(),
                    dto.gjenlevendepensjon?.toString().orEmpty(),
                    dto.gjenlevendepensjonEps?.toString().orEmpty(),
                    dto.introduksjonsstonad?.toString().orEmpty(),
                    dto.introduksjonsstonadEps?.toString().orEmpty(),
                    dto.kapitalinntekt?.toString().orEmpty(),
                    dto.kapitalinntektEps?.toString().orEmpty(),
                    dto.kontantstotte?.toString().orEmpty(),
                    dto.kontantstotteEpu?.toString().orEmpty(),
                    dto.kvalifiseringsstonad?.toString().orEmpty(),
                    dto.kvalifiseringsstonadEps?.toString().orEmpty(),
                    dto.navYtelserTilLivsopphold?.toString().orEmpty(),
                    dto.navYtelserTilLivsoppholdEps?.toString().orEmpty(),
                    dto.offentligPensjon?.toString().orEmpty(),
                    dto.offentligPensjonEps?.toString().orEmpty(),
                    dto.privatPensjon?.toString().orEmpty(),
                    dto.privatPensjonEps?.toString().orEmpty(),
                    dto.sosialstonad?.toString().orEmpty(),
                    dto.sosialstonadEps?.toString().orEmpty(),
                    dto.statensLanekasse?.toString().orEmpty(),
                    dto.statensLanekasseEps?.toString().orEmpty(),
                    dto.supplerendeStonad?.toString().orEmpty(),
                    dto.supplerendeStonadEps?.toString().orEmpty(),
                    dto.sykepenger?.toString().orEmpty(),
                    dto.sykepengerEps?.toString().orEmpty(),
                    dto.tiltakspenger?.toString().orEmpty(),
                    dto.tiltakspengerEps?.toString().orEmpty(),
                    dto.ventestonad?.toString().orEmpty(),
                    dto.ventestonadEps?.toString().orEmpty(),
                    dto.uforetrygd?.toString().orEmpty(),
                    dto.uforetrygdEps?.toString().orEmpty(),
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
