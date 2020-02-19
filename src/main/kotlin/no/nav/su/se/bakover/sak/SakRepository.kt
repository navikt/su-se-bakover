package no.nav.su.se.bakover.sak

import com.google.gson.JsonObject
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import javax.sql.DataSource

interface SakRepo {
    fun opprettSak(fnr: String): Long
    fun hentSak(fnr: String): Sak?
    fun hentSak(id: Long): Sak?
    fun hentAlleSaker(): List<Sak>
    fun hentSoknadForPerson(fnr: String): Søknad?
    fun hentSøknad(søknadId: Long): Søknad?
    fun lagreSøknad(søknadJson: JsonObject, sakId: Long): Long?
    fun hentSøknaderForSak(sakId: Long): List<Søknad>
}

internal class SakRepository(
        private val dataSource: DataSource
) : SakRepo {

    private fun String.oppdatering():Long? =
        using(sessionOf(dataSource, returnGeneratedKey = true)) {
            it.run(queryOf(this).asUpdateAndReturnGeneratedKey)
        }

    private fun <T> String.hent(rowMapping: (Row) -> T): T? = using(sessionOf(dataSource)) {
        it.run(queryOf(this).map { row -> rowMapping(row) }.asSingle)
    }

    private fun <T> String.hentListe(rowMapping: (Row) -> T): List<T> = using(sessionOf(dataSource)) {
        it.run(queryOf(this).map { row -> rowMapping(row) }.asList)
    }

    override fun hentSak(fnr: String): Sak? = "select * from sak where fnr='$fnr'".hent { toSak(it) }
    override fun hentSak(id: Long): Sak? = "select * from sak where id=$id".hent { toSak(it) }
    override fun hentAlleSaker(): List<Sak> = "select * from sak".hentListe { toSak(it) }
    override fun opprettSak(fnr: String): Long = "insert into sak (fnr) values ($fnr)".oppdatering()!! // Her bør det finnes en sak, hvis ikke bør vi feile.
    // TODO: List not single
    override fun hentSoknadForPerson(fnr: String): Søknad? = "SELECT * FROM søknad WHERE json#>>'{personopplysninger,fnr}'='$fnr'".hent { toSøknad(it) }
    override fun hentSøknaderForSak(sakId: Long): List<Søknad> = "SELECT * FROM søknad WHERE sakId=${sakId}".hentListe { toSøknad(it) }
    override fun hentSøknad(søknadId: Long): Søknad? = "SELECT * FROM søknad WHERE id=$søknadId".hent { toSøknad(it) }
    override fun lagreSøknad(søknadJson: JsonObject, sakId: Long): Long? {
        return using(sessionOf(dataSource, returnGeneratedKey = true)) { session ->
            session.run(
                queryOf(
                    "INSERT INTO søknad (json, sakId) VALUES (to_json(?::json), $sakId)",
                    søknadJson.toString()
                ).asUpdateAndReturnGeneratedKey
            )
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