package no.nav.su.se.bakover.soknad

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

class SøknadRepository(
        private val dataSource: DataSource) {

    fun hentSoknadForPerson(fnr: String): Søknad? {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT * FROM søknad WHERE json#>>'{personopplysninger,fnr}'='$fnr'").map {
                toSøknad(it)
            }.asSingle) //TODO skriv om til liste
        }
    }

    fun hentSøknad(søknadId: Long): Søknad? {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT * FROM søknad WHERE id=$søknadId").map {
                toSøknad(it)
            }.asSingle)
        }
    }

    fun lagreSøknad(søknadJson: String, sakId: Long): Long? {
        return using(sessionOf(dataSource, returnGeneratedKey = true)) { session ->
            session.run(
                    queryOf(
                            "INSERT INTO søknad (json, sakId) VALUES (to_json(?::json), $sakId)",
                            søknadJson
                    ).asUpdateAndReturnGeneratedKey
            )
        }
    }

    fun hentSøknaderForSak(sakId: Long): List<Søknad> {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT * FROM søknad WHERE sakId=${sakId}").map {
                toSøknad(it)
            }.asList)
        }
    }

    fun toSøknad(row: Row) = Søknad(
            row.long("id"),
            row.string("json"),
            row.long("sakId")
    )
}
