package no.nav.su.se.bakover.domain.historisk.aldersvedtak

import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

interface HistoriskAlderProjeksjonRepo {
    fun startProjeksjon(
        importId: UUID,
        dryRun: Boolean = false,
        maksAntallStønader: Int? = null,
    ): UUID

    fun lagreBatch(projeksjonId: UUID, importId: UUID, stønader: List<HistoriskAldersstønad>)

    fun fullførProjeksjon(
        projeksjonId: UUID,
        antallStønader: Int,
        avviksoppsummering: Map<String, Int> = emptyMap(),
        forbehold: Set<String> = emptySet(),
    )

    fun markerFeilet(projeksjonId: UUID, beskrivelse: String)

    fun hentProjeksjoner(importId: UUID): List<HistoriskAlderProjeksjonOversikt>

    fun slettProjeksjon(importId: UUID, projeksjonId: UUID): SlettHistoriskAlderProjeksjonResultat

    fun harSak(personident: String): Boolean

    /**
     * Henter alle vedtaksperioder for personen direkte fra vedtakene som ble lagret batchvis i siste fullførte
     * ordinære projeksjon. Periodene materialiseres ikke som en egen tidslinje når projeksjonen fullføres.
     */
    fun hentVedtaksperioder(personident: String): List<HistoriskVedtaksperiode>

    /**
     * Henter vedtaket og dets lagrede månedsbeløpsperioder fra siste fullførte ordinære projeksjon.
     * Personidenten brukes til tilgangskontroll og skal ikke eksponeres i API-responsen.
     */
    fun hentMånedsbeløpForVedtak(vedtakId: HistoriskVedtakId): HistoriskMånedsbeløpForVedtak

    /**
     * Henter grunnlaget for originaltidslinjen fra én eksplisitt, fullført ordinær projeksjon.
     * Projeksjonen låses senere til behandlingen slik at en nyere import ikke endrer historikken underveis.
     */
    fun hentOriginalTidslinjegrunnlag(
        projeksjonId: UUID,
        personident: String,
        periode: Periode,
    ): List<HistoriskInfotrygdTidslinjegrunnlag>

    fun hentSisteFullførteProjeksjonIdForPerson(personident: String): UUID?
}

data class HistoriskAlderProjeksjonOversikt(
    val id: UUID,
    val importId: UUID,
    val status: HistoriskAlderProjeksjonStatus,
    val dryRun: Boolean,
    val maksAntallStønader: Int?,
    val antallStønader: Int,
    val avviksoppsummering: Map<String, Int>,
    val forbehold: Set<String>,
    val opprettet: Tidspunkt,
    val fullført: Tidspunkt?,
    val feilbeskrivelse: String?,
)

enum class HistoriskAlderProjeksjonStatus {
    PÅGÅR,
    FULLFØRT,
    FEILET,
}

enum class SlettHistoriskAlderProjeksjonResultat {
    SLETTET,
    IKKE_FUNNET,
    PÅGÅR,
}

class HistoriskImportIkkeFunnetException(
    val importId: UUID,
) : IllegalStateException("Fant ikke historisk import $importId")

class HistoriskAlderProjeksjonPågårException(
    val projeksjonId: UUID,
) : IllegalStateException("Historisk aldersprojeksjon $projeksjonId pågår allerede")

data class HistoriskVedtaksperiode(
    val stønadId: HistoriskStønadId,
    val vedtakId: HistoriskVedtakId,
    val oppdragId: String?,
    val opphørskodeRaw: String?,
    val opphørsgrunn: HistoriskOpphørsgrunn?,
    val fraOgMed: LocalDate?,
    val tilOgMed: LocalDate?,
    val behandlingstypeRaw: String,
    val behandlingstype: HistoriskBehandlingstype?,
    val resultatRaw: String,
    val resultat: HistoriskResultat?,
    val bosituasjonRaw: String?,
    val bosituasjon: HistoriskBosituasjon?,
    val årligYtelsesbeløp: BigDecimal?,
    val revurderingsdato: LocalDate?,
    val registrertTidspunkt: String?,
    val endringskoder: List<String>,
    val saksreferanse: HistoriskSaksreferanse,
    val sendtTilOs: String?,
    val mottattFraOs: String?,
    val godkjentAvOs: String?,
) {
    val sakstype: Sakstype = Sakstype.ALDER
}

data class HistoriskMånedsbeløpForVedtak(
    val vedtakId: HistoriskVedtakId,
    val månedsbeløp: List<HistoriskMånedsbeløpsperiode>,
)

data class HistoriskMånedsbeløpsperiode(
    val linjeId: HistoriskOppdragLinjeId?,
    val fraOgMed: LocalDate?,
    val tilOgMed: LocalDate?,
    val sats: BigDecimal,
    val fradrag: BigDecimal,
    val fradragskoder: List<String>,
) {
    val beløp: BigDecimal = sats - fradrag
}

enum class HistoriskBehandlingstype {
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
