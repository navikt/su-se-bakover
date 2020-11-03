package no.nav.su.se.bakover.database.beregning

import no.nav.su.se.bakover.domain.beregning.beregning.IBeregning
import java.util.UUID

interface BeregningRepo {
    fun opprettBeregningForBehandling(behandlingId: UUID, beregning: IBeregning): IBeregning
    fun hentBeregningForBehandling(behandlingId: UUID): IBeregning?
    fun slettBeregningForBehandling(behandlingId: UUID)
}
