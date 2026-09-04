package no.nav.su.se.bakover.domain.historisk.aldersvedtak

import no.nav.su.se.bakover.common.tid.Tidspunkt
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
    val fraOgMed: LocalDate?,
    val tilOgMed: LocalDate?,
    val sakstype: HistoriskKode<HistoriskSakstype>,
    val resultat: HistoriskKode<HistoriskResultat>,
    val bosituasjon: HistoriskKode<HistoriskBosituasjon>?,
    val årligYtelsesbeløp: BigDecimal?,
    val registrertTidspunkt: String?,
    val gyldig: Boolean,
)
