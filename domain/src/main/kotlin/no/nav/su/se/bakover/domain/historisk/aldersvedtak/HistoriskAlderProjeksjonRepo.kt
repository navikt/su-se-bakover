package no.nav.su.se.bakover.domain.historisk.aldersvedtak

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

interface HistoriskAlderProjeksjonRepo {
    fun startProjeksjon(importId: UUID)

    fun lagreBatch(importId: UUID, stønader: List<HistoriskAldersstønad>)

    fun fullførProjeksjon(importId: UUID)

    fun markerFeilet(importId: UUID, beskrivelse: String)

    fun harSak(personident: String): Boolean

    fun hentVedtaksperioder(personident: String): List<HistoriskVedtaksperiode>

    fun hentYtelsesperioder(
        personident: String,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
    ): List<HistoriskYtelsesperiode>
}

data class HistoriskVedtaksperiode(
    val stønadId: HistoriskStønadId,
    val vedtakId: HistoriskVedtakId,
    val fraOgMed: LocalDate?,
    val tilOgMed: LocalDate?,
    val sakstype: HistoriskKode<HistoriskSakstype>,
    val resultat: HistoriskKode<HistoriskResultat>,
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
    ) : HistoriskYtelseForMåned {
        val beløpTilUtbetaling: BigDecimal
            get() = sats - fradrag
    }
}
