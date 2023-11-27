package no.nav.su.se.bakover.domain.beregning

import behandling.domain.beregning.Merknad
import behandling.domain.beregning.Merknader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class MerknaderTest {

    @Test
    fun `ugyldige kombinasjoner`() {
        listOf(
            Merknad.Beregning.Avslag.BeløpErNull,
            Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats,
            Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats,
        ).forEach {
            assertThrows<IllegalArgumentException> {
                Merknader.Beregningsmerknad().apply {
                    leggTil(Merknad.Beregning.Avslag.BeløpErNull, it)
                }
            }
        }

        listOf(
            Merknad.Beregning.Avslag.BeløpErNull,
            Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats,
            Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats,
        ).forEach {
            assertThrows<IllegalArgumentException> {
                Merknader.Beregningsmerknad().apply {
                    leggTil(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats, it)
                }
            }
        }

        listOf(
            Merknad.Beregning.Avslag.BeløpErNull,
            Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats,
            Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats,
        ).forEach {
            assertThrows<IllegalArgumentException> {
                Merknader.Beregningsmerknad().apply {
                    leggTil(Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats, it)
                }
            }
        }
    }
}
