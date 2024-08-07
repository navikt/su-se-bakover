package no.nav.su.se.bakover.domain.regulering.supplement

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.domain.tid.erFørsteDagIMåned
import no.nav.su.se.bakover.common.domain.tid.erSisteDagIMåned
import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import java.time.LocalDate

sealed interface Eksternvedtak {
    val fraOgMed: LocalDate
    val tilOgMed: LocalDate?
    val fradrag: NonEmptyList<ReguleringssupplementFor.PerType.Fradragsperiode>

    /**
     * netto
     */
    val beløp: Int

    fun overlapper(other: Eksternvedtak): Boolean {
        val thisEnd = tilOgMed ?: LocalDate.MAX
        val otherEnd = other.tilOgMed ?: LocalDate.MAX

        return this.fraOgMed <= otherEnd && other.fraOgMed <= thisEnd
    }

    fun overlapper(other: List<Eksternvedtak>): Boolean {
        return other.any { it.overlapper(this) }
    }

    fun eksterneData(): NonEmptyList<ReguleringssupplementFor.PerType.Fradragsperiode.Eksterndata> =
        fradrag.map { it.eksterndata }

    /**
     * Kaster exception dersom brutto beløpene i metadata ikke er lik
     */
    fun bruttoBeløpFraMetadata(): String {
        require(eksterneData().distinctBy { it.bruttoYtelse }.size == 1) {
            "Forventet at alle fradragene har samme brutto beløp"
        }
        return eksterneData().first().bruttoYtelse
    }

    fun toSikkerloggString(): String {
        return "Eksternvedtak(fraOgMed=$fraOgMed, tilOgMed=$tilOgMed, beløp=$beløp, fradrag=${fradrag.map { it.toSikkerloggString() }})"
    }

    data class Regulering(
        val periode: PeriodeMedOptionalTilOgMed,
        override val fradrag: NonEmptyList<ReguleringssupplementFor.PerType.Fradragsperiode>,
        /**
         * netto
         */
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
     * @param periode - Skal helst kun være 1 måned (April - måneden skal være før reguleringsperioden). Det er ikke noe garanti for at vi får tilOgMed datoen, og derfor er det en [PeriodeMedOptionalTilOgMed]
     */
    data class Endring(
        val periode: PeriodeMedOptionalTilOgMed,
        override val fradrag: NonEmptyList<ReguleringssupplementFor.PerType.Fradragsperiode>,
        /**
         * netto
         */
        override val beløp: Int,
    ) : Eksternvedtak {
        override val fraOgMed: LocalDate = periode.fraOgMed
        override val tilOgMed: LocalDate? = periode.tilOgMed

        init {
            require(fradrag.all { it.fraOgMed == fraOgMed }) {
                "Forventet tilOgMed $fraOgMed, men var ${fradrag.map { fraOgMed }}"
            }
            require(fradrag.all { it.tilOgMed == tilOgMed }) {
                "Forventet beløp $beløp, men var ${fradrag.map { beløp }}"
            }
            require(fradrag.all { it.beløp == beløp }) {
                "Forventet beløp $beløp, men var ${fradrag.map { beløp }}"
            }
            require(fradrag.all { it.vedtakstype == ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring }) {
                "Forventet at alle fradragene har vedtakstype ${ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring}, men var ${fradrag.map { it.vedtakstype }} "
            }
        }
    }
}

fun List<Eksternvedtak>.overlapper(): Boolean {
    return this.any { it.overlapper(this.minus(it)) }
}
