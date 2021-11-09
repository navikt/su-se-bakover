package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.mai
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class MerknaderTest {

    @Test
    fun `ugyldige kombinasjoner`() {
        listOf(
            Merknad.Beregning.BeløpErNull,
            Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats,
            Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats,
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
            Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats,
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
            Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats,
        ).forEach {
            assertThrows<IllegalArgumentException> {
                Merknader.Beregningsmerknad().apply {
                    leggTil(Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats, it)
                }
            }
        }
    }

    @Test
    fun `kan kombinere alle typer med endring i grunnbeløp`() {
        listOf(
            Merknad.Beregning.BeløpErNull,
            Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats,
            Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats,
        ).forEach {
            assertDoesNotThrow {
                Merknader.Beregningsmerknad().apply {
                    leggTil(
                        Merknad.Beregning.EndringGrunnbeløp(
                            gammeltGrunnbeløp = Merknad.Beregning.EndringGrunnbeløp.Detalj.forDato(1.mai(2020)),
                            nyttGrunnbeløp = Merknad.Beregning.EndringGrunnbeløp.Detalj.forDato(1.mai(2021)),
                        ),
                        it,
                    )
                }
            }
        }
    }
}
