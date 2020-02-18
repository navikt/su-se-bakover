package no.nav.su.se.bakover.sak

import com.google.gson.JsonObject
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import javax.sql.DataSource

class SakRepository(
        private val dataSource: DataSource
) {
    fun opprettSak(fnr: String): Long {
        return using(sessionOf(dataSource, returnGeneratedKey = true)) {
            it.run(queryOf("insert into sak (fnr) values ($fnr)").asUpdateAndReturnGeneratedKey)
        }!! // Her bør det finnes en sak, hvis ikke bør vi feile.
    }

    fun hentSak(fnr: String): Sak? {
        return using(sessionOf(dataSource)) {
            it.run(queryOf("select * from sak where fnr='$fnr'").map { row ->
                toSak(row)
            }.asSingle) //TODO skriv om til liste
        }
    }

    fun hentSak(id: Long): Sak? {
        return using(sessionOf(dataSource)) {
            it.run(queryOf("select * from sak where id=$id").map { row ->
                toSak(row)
            }.asSingle)
        }
    }

    fun hentAlleSaker(): List<Sak> {
        return using(sessionOf(dataSource)) {
            it.run(queryOf("select * from sak").map { row ->
                toSak(row)
            }.asList)
        }
    }

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

    fun lagreSøknad(søknadJson: JsonObject, sakId: Long): Long? {
        return using(sessionOf(dataSource, returnGeneratedKey = true)) { session ->
            session.run(
                queryOf(
                    "INSERT INTO søknad (json, sakId) VALUES (to_json(?::json), $sakId)",
                    søknadJson.toString()
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

}

private fun toSak(row: Row): Sak {
    return Sak(row.long("id"), row.string("fnr"))
}

private fun toSøknad(row: Row) = Søknad(
    row.long("id"),
    row.string("json"),
    row.long("sakId")
)