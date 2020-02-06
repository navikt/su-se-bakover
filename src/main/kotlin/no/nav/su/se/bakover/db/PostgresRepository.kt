package no.nav.su.se.bakover.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.se.bakover.soknad.Søknad
import javax.sql.DataSource

class PostgresRepository(
        private val dataSource: DataSource) {

    fun hentSøknad(fnr: String): Søknad? {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT json FROM soknad WHERE json->>'fnr'='$fnr'").map {
                it.string("json")
            }.asSingle)
        }?.let {
            Søknad(it)
        }
    }

    fun lagreSøknad(søknadJson: String) {
        using(sessionOf(dataSource)) { session ->
            session.run(
                    queryOf(
                            "INSERT INTO soknad (json) VALUES (to_json(?::json))",
                            søknadJson
                    ).asExecute
            )
        }
    }
}
