package no.nav.su.se.bakover.database.beregning

import no.nav.su.se.bakover.domain.beregning.Beregning
import java.util.UUID

interface BeregningRepo {
    fun hentBeregning(beregningId: UUID): Beregning?
}
