package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import java.time.LocalDate

sealed class Merknader {

    class Beregning {
        private val merknader: MutableList<Merknad.Beregning> = mutableListOf()

        fun leggTil(vararg merknad: Merknad.Beregning) {
            merknad.forEach { leggTil(it) }
        }

        private fun leggTil(merknad: Merknad.Beregning) {
            require(gyldigKombinasjon())
            merknader.add(merknad)
        }

        fun alle(): List<Merknad.Beregning> {
            return merknader
        }

        private fun gyldigKombinasjon(): Boolean {
            return !harBeløpErNull() && !harBeløpOverNullMenUnderToProsent() && !harSosialstønadFørerTilBeløpUnderToProsent()
        }

        private fun harBeløpErNull() =
            merknader.any { it is Merknad.Beregning.BeløpErNull }

        private fun harBeløpOverNullMenUnderToProsent() =
            merknader.any { it is Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats }

        private fun harSosialstønadFørerTilBeløpUnderToProsent() =
            merknader.any { it is Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats }
    }
}

sealed class Merknad {

    sealed class Beregning {

        data class EndringGrunnbeløp(
            val gammeltGrunnbeløp: Detalj,
            val nyttGrunnbeløp: Detalj,
        ) : Merknad.Beregning() {

            data class Detalj(
                val dato: LocalDate,
                val grunnbeløp: Int,
            ) {
                companion object {
                    fun forDato(dato: LocalDate): Detalj {
                        Grunnbeløp.`1G`.let {
                            return Detalj(
                                dato = dato,
                                grunnbeløp = it.heltallPåDato(dato),
                            )
                        }
                    }
                }
            }
        }

        /**
         * Beregnet beløp for en måned er lavere enn 2% av [Sats.HØY] som følge av sosialstønad.
         */
        object SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats : Merknad.Beregning()

        /**
         * Beregnet beløp for en måned (ex [Fradragstype.Sosialstønad]) er mellom 0 og 2% av [Sats.HØY]
         */
        object BeløpMellomNullOgToProsentAvHøySats : Merknad.Beregning()

        /**
         * Beregnet beløp for en måned (ex [Fradragstype.Sosialstønad]) er 0.
         */
        object BeløpErNull : Merknad.Beregning()

        data class MerknadMånedsberegning(
            val periode: Periode,
            val sats: Sats,
            val grunnbeløp: Int,
            val beløp: Int,
            val fradrag: List<MerknadFradrag>,
            val satsbeløp: Double,
            val fribeløpForEps: Double,
        )

        data class MerknadFradrag(
            val periode: Periode,
            val fradragstype: Fradragstype,
            val månedsbeløp: Double,
            val utenlandskInntekt: UtenlandskInntekt?,
            val tilhører: FradragTilhører,
        )
    }
}

fun Månedsberegning.toMerknadMånedsberegning() = Merknad.Beregning.MerknadMånedsberegning(
    periode = periode,
    sats = getSats(),
    grunnbeløp = getBenyttetGrunnbeløp(),
    beløp = getSumYtelse(),
    fradrag = getFradrag().map { it.toMerknadFradrag() },
    satsbeløp = getSatsbeløp(),
    fribeløpForEps = getFribeløpForEps(),
)

fun Fradrag.toMerknadFradrag() = Merknad.Beregning.MerknadFradrag(
    periode = periode,
    fradragstype = fradragstype,
    månedsbeløp = månedsbeløp,
    utenlandskInntekt = utenlandskInntekt,
    tilhører = tilhører,
)
