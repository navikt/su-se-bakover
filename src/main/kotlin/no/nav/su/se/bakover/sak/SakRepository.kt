package no.nav.su.se.bakover.sak

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.se.bakover.domain.Sak
import javax.sql.DataSource

class SakRepository(
        private val dataSource: DataSource
) {
    fun opprettSak(fnr: String): Long? {
        return using(sessionOf(dataSource, returnGeneratedKey = true)) {
            it.run(queryOf("insert into sak (fnr) values ($fnr)").asUpdateAndReturnGeneratedKey)
        }
    }

    fun hentSak(fnr: String): Sak? {
        return using(sessionOf(dataSource)) {
            it.run(queryOf("select * from sak where fnr='$fnr'").map { row ->
                toSak(row)
            }.asSingle)
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
}

private fun toSak(row: Row): Sak {
    return Sak(row.long("id"), row.string("fnr"))
}