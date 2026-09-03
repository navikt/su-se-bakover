package no.nav.su.se.bakover.domain.historisk.aldersvedtak

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Lesemodell for aldersvedtak fra Infotrygd.
 *
 * Kildedatabasen (infotrygd_suq) er allerede scopet til SU — all data i tabellene gjelder supplerende stønad.
 * Modellen er med vilje ikke en implementasjon av dagens VedtakSomKanRevurderes. Råverdier beholdes sammen med
 * tolkede verdier slik at nye eller feilregistrerte Infotrygd-koder ikke går tapt.
 *
 * Vedtakets gyldighet utledes fra endringskoder, resultat og vedtaksperiode. Ytelsestidslinjen avgrenser deretter
 * gyldige kandidater med stønads- og delytelsesperioder og velger gjeldende vedtak per måned.
 * Se docs/historisk-import-og-revurdering.md for detaljer.
 */
data class HistoriskAldersstønad(
    val stønadId: HistoriskStønadId,
    val personLøpenummer: String,
    val personident: String?,
    val startdato: HistoriskDato?,
    val oppdragId: String?,
    val opphør: HistoriskOpphør?,
    val vedtak: List<HistoriskAldersvedtak>,
)

@JvmInline
value class HistoriskStønadId(val value: String)

@JvmInline
value class HistoriskVedtakId(val value: String)

data class HistoriskAldersvedtak(
    val vedtakId: HistoriskVedtakId,
    val stønadId: HistoriskStønadId,
    val sakstype: HistoriskKode<HistoriskSakstype>,
    val resultat: HistoriskKode<HistoriskResultat>,
    val periode: HistoriskPeriode,
    val mottattDato: HistoriskDato?,
    val registrertTidspunkt: String?,
    val registrertAv: String?,
    val saksreferanse: HistoriskSaksreferanse,
    val beregningstype: String?,
    val nøkkelDl1: String?,
    val klassifiseringer: List<HistoriskStønadsklassifisering>,
    val roller: List<HistoriskRolle>,
    val beregning: HistoriskAldersberegning,
    val endringskoder: List<String>,
    val beslutninger: List<HistoriskBeslutning>,
)

data class HistoriskSaksreferanse(
    val kontornummer: String?,
    val saksblokk: String?,
    val saksnummer: String?,
    val behandlendeKontor: String?,
)

data class HistoriskPeriode(
    val fraOgMed: HistoriskDato?,
    val tilOgMed: HistoriskDato?,
)

/**
 * Råverdien beholdes sammen med tolket dato. Formatet er bekreftet ISO (`yyyy-MM-dd`), og ev. tidssuffiks ignoreres ved tolkning.
 */
data class HistoriskDato(
    val råverdi: String,
    val dato: LocalDate?,
)

data class HistoriskBeløp(
    val råverdi: String,
    val beløp: BigDecimal?,
)

data class HistoriskKode<T>(
    val råverdi: String,
    val tolketVerdi: T?,
)

enum class HistoriskSakstype {
    SØKNAD,
    REVURDERING,
    MASKINELL_OMREGNING,
    MANUELL_OMREGNING,
    MANUELL_G_REGULERING,
    MASKINELL_SATSOMREGNING,
    MASKINELL_BEREGNING,
    FLYTTESAK,
    KLAGE,
}

enum class HistoriskResultat {
    INNVILGET,
    DELVIS_INNVILGET,
    FORTSATT_INNVILGET,
    INNVILGET_NY_SITUASJON,
    ØKNING,
    REDUSERT,
    OPPHØRT,
    UENDRET,
    AVSLÅTT,
    ANNULLERT,
}

data class HistoriskStønadsklassifisering(
    val nivå: HistoriskKlassifiseringsnivå?,
    val klasse: HistoriskKode<HistoriskBosituasjon>,
)

data class HistoriskKlassifiseringsnivå(
    val kode: String,
    val tekst: String?,
)

enum class HistoriskBosituasjon {
    ENSLIG,
    EPS_OVER_67,
    EPS_UNDER_67,
    ENSLIG_MED_BOFELLESSKAP,
}

data class HistoriskRolle(
    val type: String,
    val periode: HistoriskPeriode,
    val relatertPersonLøpenummer: String?,
    val relatertPersonident: String?,
    val borSammenMed: String?,
)

data class HistoriskAldersberegning(
    val suDetaljer: List<HistoriskSuDetalj>,
    val inntekter: List<HistoriskInntekt>,
    val delytelser: List<HistoriskDelytelse>,
    val månedsbeløp: List<HistoriskMånedsbeløp>,
)

data class HistoriskSuDetalj(
    /**
     * Årlig ytelsesbeløp registrert av Infotrygd. Tilsvarer normalt månedsatsen (MS) multiplisert med 12.
     */
    val årligYtelsesbeløp: HistoriskBeløp?,
    val revurderingsdato: HistoriskDato?,
    val registrertTidspunkt: String?,
)

/**
 * Beløpet er dokumentert som årsinntekt. Beløpstypekoden skiller stønadsmottaker (suffiks M) fra ektefelle
 * (suffiks E) for typene som brukes i SU-data. BEHANDLING-feltet beholdes rått og brukes ikke til å fastslå eier.
 */
data class HistoriskInntekt(
    val type: HistoriskBeløpstype,
    val periode: HistoriskPeriode,
    val årligBeløp: HistoriskBeløp?,
    val registrertTidspunkt: String?,
)

data class HistoriskBeløpstype(
    val kode: String,
    val tekst: String?,
    val behandling: String?,
)

/**
 * Delytelsen beholdes som vedtatt resultatlinje. Reelle SU-data inneholder MS (månedsats, tillegg) og
 * valgfri FM (fradrag månedsats). FRADRAG_TILLEGG bruker F for fradrag og T for tillegg, TYPE_SATS er M
 * og TYPE_UTBETALING er L. Hver gyldige delytelsesperiode har én MS og valgfri FM, og vedtatt månedsbeløp
 * utledes derfor som MS minus FM.
 *
 * Flere delytelser kan ha samme [linjeId] innenfor et vedtak. T_MAP_DELYTELSE er et kodeverk som mapper
 * TYPE_DELYTELSE og rutine til fagområde, ikke en kobling fra vedtakets linje til en oppdragslinje.
 */
data class HistoriskDelytelse(
    val type: HistoriskDelytelsestype,
    val periode: HistoriskPeriode,
    val beløp: HistoriskBeløp?,
    val mottakerLøpenummer: String?,
    val mottakerPersonident: String?,
    val oppgjørsordning: String?,
    val satstype: String?,
    val utbetalingstype: String?,
    val linjeId: String?,
)

data class HistoriskDelytelsestype(
    val kode: String,
    val tekst: String?,
    val fradragEllerTillegg: String?,
)

/**
 * Månedsbeløp registrert i Infotrygd for en delytelsesperiode. Oppdrag kunne beregne blant annet etterbetaling,
 * mens utbetalingstransaksjoner og regnskapsdata ble dannet i betalingskjeden mot Oppdrag og UR.
 */
data class HistoriskMånedsbeløp(
    val periode: HistoriskPeriode,
    val sats: BigDecimal,
    val fradrag: BigDecimal,
    val linjeId: String?,
) {
    init {
        require(sats.signum() >= 0) { "Sats kan ikke være negativ" }
        require(fradrag.signum() >= 0) { "Fradrag kan ikke være negativt" }
        require(sats >= fradrag) { "Fradrag kan ikke være større enn sats" }
    }

    val beløpTilUtbetaling: BigDecimal
        get() = sats - fradrag
}

data class HistoriskOpphør(
    val kode: HistoriskKode<HistoriskOpphørsgrunn>,
    val dato: HistoriskDato?,
    val registrertTidspunkt: String?,
)

enum class HistoriskOpphørsgrunn {
    ANNULLERT,
    ALDERSPENSJON,
    ANNEN_ÅRSAK,
    FLYTTET,
    HØY_INNTEKT,
    INSTITUSJON,
    LANGT_UTENLANDSOPPHOLD,
    STOR_FORMUE,
    FLYTTET_TIL_UTLANDET,
    DØD,
    UTENLANDSK_ADRESSE_ELLER_GIRONUMMER,
}

data class HistoriskBeslutning(
    val beslutningId: String,
    val førsteSaksbehandler: String?,
    val førsteGodkjenning: String?,
    val førsteRegistreringstidspunkt: String?,
    val andreSaksbehandler: String?,
    val andreGodkjenning: String?,
    val andreRegistreringstidspunkt: String?,
    val sendtTilOs: String?,
    val mottattFraOs: String?,
    val godkjentAvOs: String?,
)
