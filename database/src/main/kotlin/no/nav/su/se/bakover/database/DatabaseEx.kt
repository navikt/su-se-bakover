package no.nav.su.se.bakover.database

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.using
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.toTidspunkt
import java.sql.Array
import java.util.UUID
import javax.sql.DataSource

internal fun String.oppdatering(
    params: Map<String, Any?>,
    session: Session
) = session.run(queryOf(statement = this, paramMap = params).asUpdate)

internal fun <T> String.hent(
    params: Map<String, Any> = emptyMap(),
    session: Session,
    rowMapping: (Row) -> T
): T? =
    session.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle)

internal fun <T> String.hentListe(
    params: Map<String, Any> = emptyMap(),
    session: Session,
    rowMapping: (Row) -> T
): List<T> = session.run(queryOf(this, params).map { row -> rowMapping(row) }.asList)

internal fun Row.uuid(name: String) = UUID.fromString(string(name))
internal fun Row.uuid30(name: String) = UUID30.fromString(string(name))
internal fun Row.tidspunkt(name: String) = this.instant(name).toTidspunkt()

internal fun Session.inClauseWith(values: List<String>): Array =
    this.connection.underlying.createArrayOf("text", values.toTypedArray())

internal fun <T> DataSource.withSession(block: (session: Session) -> T): T {
    return using(sessionOf(this)) { block(it) }
}

internal fun String.antall(
    params: Map<String, Any> = emptyMap(),
    session: Session
): Long = session.run(queryOf(this, params).map { row -> row.long("count") }.asSingle)!!
