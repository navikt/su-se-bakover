package no.nav.su.se.bakover.database

import kotliquery.Row
import kotliquery.queryOf
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.toTidspunkt
import java.util.UUID

internal fun String.oppdatering(params: Map<String, Any?>, session: Session) =
    session.run(
        queryOf(
            this,
            params
        ).asUpdate
    )

internal fun <T> String.hent(params: Map<String, Any> = emptyMap(), session: Session, rowMapping: (Row) -> T): T? =
    session.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle)

internal fun <T> String.hentListe(
    params: Map<String, Any> = emptyMap(),
    session: Session,
    rowMapping: (Row) -> T
): List<T> = session.run(queryOf(this, params).map { row -> rowMapping(row) }.asList)

internal fun Row.uuid(name: String) = UUID.fromString(string(name))
internal fun Row.uuid30(name: String) = UUID30.fromString(string(name))
internal fun Row.toTidspunkt(name: String) = this.instant(name).toTidspunkt()
