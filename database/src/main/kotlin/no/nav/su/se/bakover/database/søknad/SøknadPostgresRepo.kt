package no.nav.su.se.bakover.database.søknad

import kotliquery.using
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.sessionOf
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal.hentSøknadInternal
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID
import javax.sql.DataSource

internal class SøknadPostgresRepo(
    private val dataSource: DataSource
) : SøknadRepo {
    override fun hentSøknad(søknadId: UUID) = using(sessionOf(dataSource)) { hentSøknadInternal(søknadId, it) }

    override fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad {
        "insert into søknad (id, sakId, søknadInnhold, opprettet) values (:id, :sakId, to_json(:soknad::json), :opprettet)".oppdatering(
            mapOf(
                "id" to søknad.id,
                "sakId" to sakId,
                "soknad" to objectMapper.writeValueAsString(søknad.søknadInnhold),
                "opprettet" to søknad.opprettet
            )
        )
        return hentSøknad(søknad.id)!!
    }

    private fun String.oppdatering(params: Map<String, Any?>) {
        using(sessionOf(dataSource)) {
            this.oppdatering(params, it)
        }
    }
}
