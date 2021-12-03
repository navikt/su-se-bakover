package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype

sealed class Merknader {

    class Beregningsmerknad {
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
            merknader.any { it is Merknad.Beregning.SosialstønadOgAvkortingFørerTilBeløpLavereEnnToProsentAvHøySats }
    }
}

sealed class Merknad {

    sealed class Beregning {
        /**
         * Beregnet beløp for en måned er lavere enn 2% av [Sats.HØY] som følge av sosialstønad.
         */
        object SosialstønadOgAvkortingFørerTilBeløpLavereEnnToProsentAvHøySats : Merknad.Beregning()

        /**
         * Beregnet beløp for en måned (ex [Fradragstype.Sosialstønad]) er mellom 0 og 2% av [Sats.HØY]
         */
        object BeløpMellomNullOgToProsentAvHøySats : Merknad.Beregning()

        /**
         * Beregnet beløp for en måned (ex [Fradragstype.Sosialstønad]) er 0.
         */
        object BeløpErNull : Merknad.Beregning()
    }
}
