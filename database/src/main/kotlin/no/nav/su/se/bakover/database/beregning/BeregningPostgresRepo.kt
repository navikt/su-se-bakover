package no.nav.su.se.bakover.database.beregning

import kotliquery.using
import no.nav.su.se.bakover.database.beregning.BeregningRepoInternal.hentBeregning
import no.nav.su.se.bakover.database.sessionOf
import no.nav.su.se.bakover.domain.beregning.Beregning
import java.util.UUID
import javax.sql.DataSource

internal class BeregningPostgresRepo(
    private val dataSource: DataSource
) : BeregningRepo {
    override fun hentBeregning(beregningId: UUID): Beregning? =
        using(sessionOf(dataSource)) { hentBeregning(beregningId, it) }
}
