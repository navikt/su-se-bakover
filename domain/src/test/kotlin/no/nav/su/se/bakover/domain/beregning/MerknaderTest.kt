package no.nav.su.se.bakover.domain.beregning

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class MerknaderTest {

    @Test
    fun `ugyldige kombinasjoner`() {
        listOf(
            Merknad.Beregning.BeløpErNull,
            Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats,
            Merknad.Beregning.SosialstønadOgAvkortingFørerTilBeløpLavereEnnToProsentAvHøySats,
        ).forEach {
            assertThrows<IllegalArgumentException> {
                Merknader.Beregningsmerknad().apply {
                    leggTil(Merknad.Beregning.BeløpErNull, it)
                }
            }
        }

        listOf(
            Merknad.Beregning.BeløpErNull,
            Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats,
            Merknad.Beregning.SosialstønadOgAvkortingFørerTilBeløpLavereEnnToProsentAvHøySats,
        ).forEach {
            assertThrows<IllegalArgumentException> {
                Merknader.Beregningsmerknad().apply {
                    leggTil(Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats, it)
                }
            }
        }

        listOf(
            Merknad.Beregning.BeløpErNull,
            Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats,
            Merknad.Beregning.SosialstønadOgAvkortingFørerTilBeløpLavereEnnToProsentAvHøySats,
        ).forEach {
            assertThrows<IllegalArgumentException> {
                Merknader.Beregningsmerknad().apply {
                    leggTil(Merknad.Beregning.SosialstønadOgAvkortingFørerTilBeløpLavereEnnToProsentAvHøySats, it)
                }
            }
        }
    }
}
