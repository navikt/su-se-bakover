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
            session.run(queryOf("SELECT data FROM soknad WHERE fnr = ? ORDER BY id DESC LIMIT 1", fnr).map {
                it.string("json")
            }.asSingle)
        }?.let {
            Søknad(it)
        }
    }

    private fun lagreSøknad(ident: String, søknadJson: String) {
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "INSERT INTO soknad (fnr, data) VALUES (?, (to_json(?::json)))",
                    ident, søknadJson
                ).asExecute
            )
        }
    }

}
