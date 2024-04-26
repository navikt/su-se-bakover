package no.nav.su.se.bakover.domain.regulering.supplement

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.domain.tid.erFørsteDagIMåned
import no.nav.su.se.bakover.common.domain.tid.erSisteDagIMåned
import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.common.tid.periode.Måned
import java.time.LocalDate

sealed interface Eksternvedtak {
    val fraOgMed: LocalDate
    val tilOgMed: LocalDate?
    val fradrag: NonEmptyList<ReguleringssupplementFor.PerType.Fradragsperiode>
    val beløp: Int

    fun overlapper(other: Eksternvedtak): Boolean {
        val thisEnd = tilOgMed ?: LocalDate.MAX
        val otherEnd = other.tilOgMed ?: LocalDate.MAX

        return this.fraOgMed <= otherEnd && other.fraOgMed <= thisEnd
    }

    fun overlapper(other: List<Eksternvedtak>): Boolean {
        return other.any { it.overlapper(this) }
    }

    // TODO - test
    fun eksterneData(): NonEmptyList<ReguleringssupplementFor.PerType.Fradragsperiode.Eksterndata> =
        fradrag.map { it.eksterndata }

    data class Regulering(
        val periode: PeriodeMedOptionalTilOgMed,
        override val fradrag: NonEmptyList<ReguleringssupplementFor.PerType.Fradragsperiode>,
        override val beløp: Int,
    ) : Eksternvedtak {
        override val fraOgMed = periode.fraOgMed
        override val tilOgMed = periode.tilOgMed

        init {
            require(fradrag.all { it.fraOgMed == fraOgMed }) {
                "Forventet tilOgMed $fraOgMed, men var ${fradrag.map { fraOgMed }}"
            }
            require(fradrag.all { it.tilOgMed == tilOgMed }) {
                "Forventet tilOgMed $tilOgMed, men var ${fradrag.map { tilOgMed }}"
            }
            require(fradrag.all { it.beløp == beløp }) {
                "Forventet beløp $beløp, men var ${fradrag.map { beløp }}"
            }
            require(fradrag.all { it.vedtakstype == ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering }) {
                "Forventet at alle fradragene har vedtakstype ${ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering}, men var ${fradrag.map { it.vedtakstype }} "
            }
            require(fraOgMed.erFørsteDagIMåned()) {
                "Forventer at vedtakene løper over hele måneder, men var: fraOgMed: $fraOgMed, tilOgMed: $tilOgMed"
            }
            tilOgMed?.let {
                require(it.erSisteDagIMåned()) {
                    "Forventer at vedtakene løper over hele måneder, men var: fraOgMed: $fraOgMed, tilOgMed: $tilOgMed"
                }
            }
        }
    }

    /**
     * Vi ønsker kun 1 måned med endringsdato i de tilfellene Pesys og SU reguleres samtidig.
     * Denne måneden skal være før reguleringsperioden.
     */
    data class Endring(
        val måned: Måned,
        override val fradrag: NonEmptyList<ReguleringssupplementFor.PerType.Fradragsperiode>,
        override val beløp: Int,
    ) : Eksternvedtak {
        override val fraOgMed: LocalDate = måned.fraOgMed
        override val tilOgMed: LocalDate = måned.tilOgMed

        init {
            require(fradrag.all { it.fraOgMed == fraOgMed })
            require(fradrag.all { it.tilOgMed == tilOgMed })
            require(fradrag.all { it.beløp == beløp })
            require(fradrag.all { it.vedtakstype == ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring })
        }
    }
}

fun List<Eksternvedtak>.overlapper(): Boolean {
    return this.any { it.overlapper(this.minus(it)) }
}
