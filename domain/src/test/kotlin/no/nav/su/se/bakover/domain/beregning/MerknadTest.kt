package no.nav.su.se.bakover.domain.beregning

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

internal class MerknadTest {
    @Test
    fun `kan ikke kombinere økning med`() {
        listOf(
            mock<Merknad.ØktYtelse>(),
            mock<Merknad.NyYtelse>(),
            mock<Merknad.RedusertYtelse>(),
            mock<Merknad.EndringUnderTiProsent>(),
        ).forEach {
            assertThrows<IllegalStateException> {
                Merknader().apply {
                    leggTil(mock<Merknad.ØktYtelse>(), it)
                }
            }
        }
    }

    @Test
    fun `kan ikke kombinere reduksjon med`() {
        listOf(
            mock<Merknad.ØktYtelse>(),
            mock<Merknad.NyYtelse>(),
            mock<Merknad.RedusertYtelse>(),
            mock<Merknad.EndringUnderTiProsent>(),
        ).forEach {
            assertThrows<IllegalStateException> {
                Merknader().apply {
                    leggTil(mock<Merknad.RedusertYtelse>(), it)
                }
            }
        }
    }

    @Test
    fun `kan ikke kombinere ny ytelse med`() {
        listOf(
            mock<Merknad.ØktYtelse>(),
            mock<Merknad.NyYtelse>(),
            mock<Merknad.RedusertYtelse>(),
            mock<Merknad.EndringUnderTiProsent>(),
        ).forEach {
            assertThrows<IllegalStateException> {
                Merknader().apply {
                    leggTil(mock<Merknad.NyYtelse>(), it)
                }
            }
        }
    }

    @Test
    fun `kan ikke kombinere endring under 10 prosent med`() {
        listOf(
            mock<Merknad.ØktYtelse>(),
            mock<Merknad.NyYtelse>(),
            mock<Merknad.RedusertYtelse>(),
            mock<Merknad.EndringUnderTiProsent>(),
        ).forEach {
            assertThrows<IllegalStateException> {
                Merknader().apply {
                    leggTil(mock<Merknad.EndringUnderTiProsent>(), it)
                }
            }
        }
    }

    @Test
    fun `kan kombinere alle typer med endring i grunnbeløp`() {
        listOf(
            mock<Merknad.ØktYtelse>(),
            mock<Merknad.NyYtelse>(),
            mock<Merknad.RedusertYtelse>(),
            mock<Merknad.EndringUnderTiProsent>(),
        ).forEach {
            assertDoesNotThrow {
                Merknader().apply {
                    leggTil(mock<Merknad.EndringGrunnbeløp>(), it)
                }
            }
        }
    }
}
