package no.nav.su.se.bakover.database.beregning

import no.nav.su.se.bakover.domain.beregning.Beregning
import java.util.UUID

interface BeregningRepo {
    fun opprettBeregningForBehandling(behandlingId: UUID, beregning: Beregning): Beregning
    fun hentBeregningForBehandling(behandlingId: UUID): Beregning?
    fun slettBeregningForBehandling(behandlingId: UUID)
}
