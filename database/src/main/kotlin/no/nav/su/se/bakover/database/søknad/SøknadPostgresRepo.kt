package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.søknad.LukketSøknadJson.Companion.toJson
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal.hentSøknadInternal
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID
import javax.sql.DataSource

internal class SøknadPostgresRepo(
    private val dataSource: DataSource
) : SøknadRepo {
    override fun hentSøknad(søknadId: UUID) = dataSource.withSession { hentSøknadInternal(søknadId, it) }

    override fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad {
        dataSource.withSession { session ->
            "insert into søknad (id, sakId, søknadInnhold, opprettet) values (:id, :sakId, to_json(:soknad::json), :opprettet)".oppdatering(
                mapOf(
                    "id" to søknad.id,
                    "sakId" to sakId,
                    "soknad" to objectMapper.writeValueAsString(søknad.søknadInnhold),
                    "opprettet" to søknad.opprettet
                ),
                session
            )
        }
        return hentSøknad(søknad.id)!!
    }

    override fun lukkSøknad(søknadId: UUID, lukket: Søknad.Lukket) {
        dataSource.withSession { session ->
            "update søknad set lukket=to_json(:lukket::json) where id=:id".oppdatering(
                mapOf(
                    "id" to søknadId,
                    "lukket" to objectMapper.writeValueAsString(lukket.toJson())
                ),
                session
            )
        }
    }

    override fun harSøknadPåbegyntBehandling(søknadId: UUID): Boolean {
        return dataSource.withSession { session ->
            "select * from behandling where søknadId=:soknadId".hentListe(
                mapOf("soknadId" to søknadId), session
            ) { it.stringOrNull("søknadId") }
        }.isNotEmpty()
    }
}
