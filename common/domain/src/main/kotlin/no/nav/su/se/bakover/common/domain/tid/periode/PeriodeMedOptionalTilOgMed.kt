package no.nav.su.se.bakover.common.domain.tid.periode

import no.nav.su.se.bakover.common.tid.periode.Periode
import java.time.LocalDate

/**
 * Periode som går fra en gitt dato til opphør.
 * Visse stønader/ytelser i nav setter ikke en tilOgMed dato på ytelser som løper over mange år.
 */
data class PeriodeMedOptionalTilOgMed(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate? = null,
) {

    fun overlapper(other: PeriodeMedOptionalTilOgMed): Boolean {
        val thisEnd = tilOgMed ?: LocalDate.MAX
        val otherEnd = other.tilOgMed ?: LocalDate.MAX

        return this.fraOgMed <= otherEnd && other.fraOgMed <= thisEnd
    }

    @JvmName("overlapper mot liste av evige perioder")
    fun overlapper(other: List<PeriodeMedOptionalTilOgMed>): Boolean {
        return other.any { it.overlapper(this) }
    }

    fun overlapper(other: Periode): Boolean {
        val thisEnd = tilOgMed ?: LocalDate.MAX
        val otherEnd = other.tilOgMed

        return this.fraOgMed <= otherEnd && other.fraOgMed <= thisEnd
    }

    fun overlapper(other: List<Periode>): Boolean {
        return other.any { this.overlapper(it) }
    }
}
