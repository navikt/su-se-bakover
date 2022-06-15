package no.nav.su.se.bakover.database

import kotliquery.Row
import kotliquery.queryOf
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.beregning.Beregning
import java.sql.Array

private fun sjekkUgyldigParameternavn(params: Map<String, Any?>) {
    require(params.keys.none { it.contains(Regex("[æÆøØåÅ]")) }) { "Parameter-mapping forstår ikke særnorske tegn" }
}

private fun sjekkAtOppdaterInneholderWhere(sql: String) {
    require(sql.lowercase().contains("where")) { "Ikke lov med update uten where" }
}

/**
 * Krev at [Beregning] sendes inn som et objekt, slik at [Session] kan garantere for lik serialisering i alle tilfeller.
 * @see [Session.setParam]
 */
private fun sjekkTypeForBeregning(params: Map<String, Any?>) {
    if (params.contains("beregning")) {
        require(params["beregning"] == null || params["beregning"] is Beregning)
    }
}

internal fun String.oppdatering(
    params: Map<String, Any?>,
    session: Session,
) {
    sjekkUgyldigParameternavn(params)
    sjekkAtOppdaterInneholderWhere(this)
    sjekkTypeForBeregning(params)
    session.run(queryOf(statement = this, paramMap = params).asUpdate)
}

internal fun String.insert(
    params: Map<String, Any?>,
    session: Session,
) {
    sjekkUgyldigParameternavn(params)
    sjekkTypeForBeregning(params)
    session.run(queryOf(statement = this, paramMap = params).asUpdate)
}

internal fun <T> String.hent(
    params: Map<String, Any> = emptyMap(),
    session: Session,
    rowMapping: (Row) -> T,
): T? {
    sjekkUgyldigParameternavn(params)
    return session.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle)
}

internal fun <T> String.hentListe(
    params: Map<String, Any> = emptyMap(),
    session: Session,
    rowMapping: (Row) -> T,
): List<T> {
    sjekkUgyldigParameternavn(params)
    return session.run(queryOf(this, params).map { row -> rowMapping(row) }.asList)
}

internal fun Row.uuid30(name: String) = UUID30.fromString(string(name))
internal fun Row.uuid30OrNull(name: String) = stringOrNull(name)?.let { UUID30.fromString(it) }
internal fun Row.tidspunkt(name: String) = this.instant(name).toTidspunkt()
internal fun Row.tidspunktOrNull(name: String) = this.instantOrNull(name)?.toTidspunkt()

// Row.boolean(value) returnerer false dersom value er null
internal fun Row.booleanOrNull(name: String): Boolean? = this.anyOrNull(name)?.let { this.boolean(name) }
internal fun Row.periode(fraOgMed: String, tilOgMed: String): Periode {
    return Periode.create(localDate(fraOgMed), localDate(tilOgMed))
}

internal fun Session.inClauseWith(values: List<String>): Array =
    this.connection.underlying.createArrayOf("text", values.toTypedArray())

internal fun String.antall(
    params: Map<String, Any> = emptyMap(),
    session: Session,
): Long {
    sjekkUgyldigParameternavn(params)
    return session.run(queryOf(this, params).map { row -> row.long("count") }.asSingle)!!
}
