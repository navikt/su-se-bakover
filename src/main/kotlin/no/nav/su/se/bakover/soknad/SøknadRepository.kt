package no.nav.su.se.bakover.soknad

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

class SøknadRepository(
        private val dataSource: DataSource) {

    fun hentSoknadForPerson(fnr: String): Søknad? {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT json FROM soknad WHERE json#>>'{personopplysninger,fnr}'='$fnr'").map {
                it.string("json")
            }.asSingle) //TODO skriv om til liste
        }?.let {
            Søknad(it)
        }
    }

    fun hentSøknad(søknadId: Long): Søknad? {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT json FROM soknad WHERE id=$søknadId").map {
                it.string("json")
            }.asSingle)
        }?.let {
            Søknad(it)
        }
    }

    fun lagreSøknad(søknadJson: String): Long? {
        return using(sessionOf(dataSource, returnGeneratedKey = true)) { session ->
            session.run(
                    queryOf(
                            "INSERT INTO soknad (json) VALUES (to_json(?::json))",
                            søknadJson
                    ).asUpdateAndReturnGeneratedKey
            )
        }
    }
}
