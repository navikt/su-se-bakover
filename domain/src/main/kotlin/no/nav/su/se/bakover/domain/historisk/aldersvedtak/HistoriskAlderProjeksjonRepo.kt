package no.nav.su.se.bakover.domain.historisk.aldersvedtak

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
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

    fun hentVedtaksperioder(personident: String): List<HistoriskVedtaksperiode>

    fun hentYtelsesperioder(
        personident: String,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
    ): List<HistoriskYtelsesperiode>
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

data class HistoriskYtelsesperiode(
    val stønadId: HistoriskStønadId,
    val vedtakId: HistoriskVedtakId,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val sats: BigDecimal,
    val fradrag: BigDecimal,
    val bosituasjon: HistoriskKode<HistoriskBosituasjon>?,
    val årligYtelsesbeløp: BigDecimal?,
) {
    init {
        require(fraOgMed <= tilOgMed) { "Ytelsesperioden kan ikke være baklengs" }
        require(sats.signum() >= 0) { "Sats kan ikke være negativ" }
        require(fradrag.signum() >= 0) { "Fradrag kan ikke være negativt" }
        require(sats >= fradrag) { "Fradrag kan ikke være større enn sats" }
    }

    val beløpTilUtbetaling: BigDecimal
        get() = sats - fradrag
}

data class HistoriskYtelsestidslinje(
    val personident: String,
    val fraOgMed: YearMonth,
    val tilOgMed: YearMonth,
    val måneder: List<HistoriskYtelseForMåned>,
) {
    init {
        require(fraOgMed <= tilOgMed) { "Tidslinjen kan ikke være baklengs" }
        val forventedeMåneder =
            generateSequence(fraOgMed) { it.plusMonths(1) }
                .takeWhile { it <= tilOgMed }
                .toList()
        require(måneder.map { it.måned } == forventedeMåneder) {
            "Tidslinjen må inneholde nøyaktig én sortert tilstand per måned"
        }
    }
}

sealed interface HistoriskYtelseForMåned {
    val måned: YearMonth

    data class IngenYtelse(
        override val måned: YearMonth,
    ) : HistoriskYtelseForMåned

    data class Ytelse(
        override val måned: YearMonth,
        val stønadId: HistoriskStønadId,
        val vedtakId: HistoriskVedtakId,
        val sats: BigDecimal,
        val fradrag: BigDecimal,
        val bosituasjon: HistoriskKode<HistoriskBosituasjon>?,
        val årligYtelsesbeløp: BigDecimal?,
    ) : HistoriskYtelseForMåned {
        val beløpTilUtbetaling: BigDecimal
            get() = sats - fradrag
    }
}
