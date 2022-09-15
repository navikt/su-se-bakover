package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype

sealed class Merknader {

    class Beregningsmerknad(
        private val merknader: MutableList<Merknad.Beregning> = mutableListOf(),
    ) {

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
            return !harBeløpErNull() && !harBeløpOverNullMenUnderToProsent() && !harSosialstønadFørerTilBeløpUnderToProsent() && !harAvkortingFørerTilBeløpUnderToProsent()
        }

        private fun harBeløpErNull() =
            merknader.any { it is Merknad.Beregning.Avslag.BeløpErNull }

        private fun harBeløpOverNullMenUnderToProsent() =
            merknader.any { it is Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats }

        private fun harSosialstønadFørerTilBeløpUnderToProsent() =
            merknader.any { it is Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats }

        private fun harAvkortingFørerTilBeløpUnderToProsent() =
            merknader.any { it is Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats }

        override fun equals(other: Any?): Boolean {
            return other is Beregningsmerknad && merknader == other.merknader
        }

        override fun hashCode(): Int {
            return merknader.hashCode()
        }

        override fun toString(): String {
            return "Beregningsmerknad(merknader=$merknader)"
        }
    }
}

sealed class Merknad {

    sealed class Beregning {
        sealed class Avslag : Beregning() {
            /**
             * Beregnet beløp for en måned (ex [Fradragstype.Sosialstønad]) er mellom 0 og 2% av [Satskategori.Høy]
             */
            object BeløpMellomNullOgToProsentAvHøySats : Avslag() {
                override fun toString(): String = this.javaClass.simpleName
            }

            /**
             * Beregnet beløp for en måned (ex [Fradragstype.Sosialstønad]) er 0.
             */
            object BeløpErNull : Avslag() {
                override fun toString(): String = this.javaClass.simpleName
            }
        }

        /**
         * Beregnet beløp for en måned er lavere enn 2% av [Satskategori.Høy] som følge av avkorting.
         */
        object AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats : Beregning() {
            override fun toString(): String = this.javaClass.simpleName
        }

        /**
         * Beregnet beløp for en måned er lavere enn 2% av [Satskategori.Høy] som følge av sosialstønad.
         */
        object SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats : Beregning() {
            override fun toString(): String = this.javaClass.simpleName
        }
    }
}
