package no.nav.su.se.bakover.database.søknad

import kotliquery.using
import no.nav.su.se.bakover.database.sessionOf
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal.hentSøknadInternal
import java.util.UUID
import javax.sql.DataSource

internal class SøknadPostgresRepo(
    private val dataSource: DataSource
) : SøknadRepo {
    override fun hentSøknad(søknadId: UUID) = using(sessionOf(dataSource)) { hentSøknadInternal(søknadId, it) }
}
