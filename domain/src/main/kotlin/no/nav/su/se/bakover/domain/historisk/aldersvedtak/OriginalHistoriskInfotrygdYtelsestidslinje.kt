package no.nav.su.se.bakover.domain.historisk.aldersvedtak

import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Leser grunnlaget for originaltidslinjen til historisk Infotrygd-revurdering.
 * Holdes adskilt fra [HistoriskAlderProjeksjonRepo], som eier import og projeksjon.
 */
interface HistoriskInfotrygdTidslinjeRepo {
    fun hentSisteFullførteProjeksjonIdForPerson(personident: String): UUID?

    /**
     * Henter vedtakene i én fullført, ordinær projeksjon som overlapper perioden, med stønadens avgrensning
     * og vedtakets månedsbeløp. Projeksjonen låses til behandlingen, slik at en nyere import ikke endrer den.
     */
    fun hentOriginalTidslinjegrunnlag(
        projeksjonId: UUID,
        personident: String,
        periode: Periode,
    ): List<HistoriskInfotrygdTidslinjegrunnlag>
}

data class HistoriskInfotrygdTidslinjegrunnlag(
    val vedtak: HistoriskInfotrygdTidslinjevedtak,
    val stønadsavgrensning: HistoriskStønadsavgrensning,
    val månedsbeløp: List<HistoriskInfotrygdBeløpsperiode>,
) {
    init {
        require(vedtak.stønadId == stønadsavgrensning.stønadId) {
            "Vedtak og stønadsavgrensning må tilhøre samme stønad"
        }
    }
}

data class HistoriskInfotrygdTidslinjevedtak(
    val stønadId: HistoriskStønadId,
    val vedtakId: HistoriskVedtakId,
    val oppdragId: String?,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val resultat: HistoriskResultat?,
    val bosituasjon: HistoriskBosituasjon?,
    val registrertTidspunkt: LocalDateTime?,
    val endringskoder: List<String>,
)

data class HistoriskStønadsavgrensning(
    val stønadId: HistoriskStønadId,
    val fraOgMed: LocalDate?,
    val tilOgMed: LocalDate?,
)

data class HistoriskInfotrygdBeløpsperiode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate?,
    val sats: BigDecimal,
    val fradrag: BigDecimal,
    val fradragskoder: List<String>,
)

data class OriginalHistoriskInfotrygdYtelsestidslinje(
    val projeksjonId: UUID,
    val periode: Periode,
    val måneder: Map<Måned, HistoriskInfotrygdYtelseForMåned>,
) {
    init {
        require(periode.måneder() == måneder.keys.toList()) {
            "Tidslinjen må inneholde alle månedene i perioden i stigende rekkefølge"
        }
    }

    companion object {
        fun bygg(
            projeksjonId: UUID,
            periode: Periode,
            grunnlag: List<HistoriskInfotrygdTidslinjegrunnlag>,
        ): OriginalHistoriskInfotrygdYtelsestidslinje {
            val kandidater = grunnlag.mapNotNull { it.tilKandidat() }
            val måneder = periode.måneder().associateWith { måned ->
                val gjeldende = kandidater
                    .filter { it.dekker(måned) }
                    .maxWithOrNull(compareBy<Kandidat>({ it.registrertTidspunkt }, { it.grunnlag.vedtak.vedtakId.value }))

                gjeldende?.tilYtelse(måned)
                    ?: HistoriskInfotrygdYtelseForMåned.IngenYtelse(
                        måned = måned,
                        årsak = HistoriskInfotrygdIngenYtelseÅrsak.INGEN_GJELDENDE_VEDTAK,
                    )
            }
            return OriginalHistoriskInfotrygdYtelsestidslinje(
                projeksjonId = projeksjonId,
                periode = periode,
                måneder = måneder,
            )
        }
    }
}

sealed interface HistoriskInfotrygdYtelseForMåned {
    val måned: Måned

    data class Ytelse(
        override val måned: Måned,
        val stønadId: HistoriskStønadId,
        val vedtakId: HistoriskVedtakId,
        val oppdragId: String?,
        val bosituasjon: HistoriskBosituasjon?,
        val sats: BigDecimal,
        val fradrag: BigDecimal,
        val fradragskoder: List<String>,
    ) : HistoriskInfotrygdYtelseForMåned {
        val beløp: BigDecimal = sats - fradrag
    }

    data class IngenYtelse(
        override val måned: Måned,
        val årsak: HistoriskInfotrygdIngenYtelseÅrsak,
        val stønadId: HistoriskStønadId? = null,
        val vedtakId: HistoriskVedtakId? = null,
        val oppdragId: String? = null,
    ) : HistoriskInfotrygdYtelseForMåned
}

enum class HistoriskInfotrygdIngenYtelseÅrsak {
    INGEN_GJELDENDE_VEDTAK,
    OPPHØRT,
    MANGLER_MÅNEDSBELØP,
    FLERE_MÅNEDSBELØP,
}

private data class Kandidat(
    val grunnlag: HistoriskInfotrygdTidslinjegrunnlag,
    val registrertTidspunkt: LocalDateTime,
) {
    fun dekker(måned: Måned): Boolean =
        grunnlag.vedtak.periodeDekker(måned) &&
            grunnlag.stønadsavgrensning.periodeDekker(måned)

    fun tilYtelse(måned: Måned): HistoriskInfotrygdYtelseForMåned {
        if (grunnlag.vedtak.resultat == HistoriskResultat.OPPHØRT) {
            return HistoriskInfotrygdYtelseForMåned.IngenYtelse(
                måned = måned,
                årsak = HistoriskInfotrygdIngenYtelseÅrsak.OPPHØRT,
                stønadId = grunnlag.vedtak.stønadId,
                vedtakId = grunnlag.vedtak.vedtakId,
                oppdragId = grunnlag.vedtak.oppdragId,
            )
        }
        val beløpsperioder = grunnlag.månedsbeløp.filter { it.periodeDekker(måned) }
        if (beløpsperioder.isEmpty()) {
            return HistoriskInfotrygdYtelseForMåned.IngenYtelse(
                måned = måned,
                årsak = HistoriskInfotrygdIngenYtelseÅrsak.MANGLER_MÅNEDSBELØP,
                vedtakId = grunnlag.vedtak.vedtakId,
            )
        }
        if (beløpsperioder.size > 1) {
            return HistoriskInfotrygdYtelseForMåned.IngenYtelse(
                måned = måned,
                årsak = HistoriskInfotrygdIngenYtelseÅrsak.FLERE_MÅNEDSBELØP,
                vedtakId = grunnlag.vedtak.vedtakId,
            )
        }

        val beløpsperiode = beløpsperioder.single()
        return HistoriskInfotrygdYtelseForMåned.Ytelse(
            måned = måned,
            stønadId = grunnlag.vedtak.stønadId,
            vedtakId = grunnlag.vedtak.vedtakId,
            oppdragId = grunnlag.vedtak.oppdragId,
            bosituasjon = grunnlag.vedtak.bosituasjon,
            sats = beløpsperiode.sats,
            fradrag = beløpsperiode.fradrag,
            fradragskoder = beløpsperiode.fradragskoder,
        )
    }
}

private fun HistoriskInfotrygdTidslinjegrunnlag.tilKandidat(): Kandidat? {
    if (vedtak.endringskoder.any { it.trim().uppercase() in ugyldigeEndringskoder }) return null
    if (vedtak.resultat !in ytelsesresultater) return null
    if (!vedtak.harGyldigPeriode() || !stønadsavgrensning.harGyldigPeriode()) return null

    return Kandidat(
        grunnlag = this,
        registrertTidspunkt = vedtak.registrertTidspunkt ?: LocalDateTime.MIN,
    )
}

private fun HistoriskInfotrygdTidslinjevedtak.harGyldigPeriode(): Boolean =
    !fraOgMed.isAfter(tilOgMed)

private fun HistoriskStønadsavgrensning.harGyldigPeriode(): Boolean =
    fraOgMed == null || tilOgMed == null || !fraOgMed.isAfter(tilOgMed)

private fun HistoriskInfotrygdTidslinjevedtak.periodeDekker(måned: Måned): Boolean =
    !fraOgMed.isAfter(måned.fraOgMed) && !tilOgMed.isBefore(måned.tilOgMed)

private fun HistoriskStønadsavgrensning.periodeDekker(måned: Måned): Boolean =
    (fraOgMed == null || !fraOgMed.isAfter(måned.fraOgMed)) &&
        (tilOgMed == null || !tilOgMed.isBefore(måned.tilOgMed))

private fun HistoriskInfotrygdBeløpsperiode.periodeDekker(måned: Måned): Boolean =
    !fraOgMed.isAfter(måned.fraOgMed) &&
        (tilOgMed == null || !tilOgMed.isBefore(måned.tilOgMed))

private val ugyldigeEndringskoder = setOf("AN", "UA")

private val ytelsesresultater = setOf(
    HistoriskResultat.INNVILGET,
    HistoriskResultat.DELVIS_INNVILGET,
    HistoriskResultat.FORTSATT_INNVILGET,
    HistoriskResultat.INNVILGET_NY_SITUASJON,
    HistoriskResultat.ØKNING,
    HistoriskResultat.REDUSERT,
    HistoriskResultat.OPPHØRT,
)
