package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.domain.behandling.Revurdering
import javax.sql.DataSource

internal class RevurderingPostgresRepo(
    private val dataSource: DataSource,
)
