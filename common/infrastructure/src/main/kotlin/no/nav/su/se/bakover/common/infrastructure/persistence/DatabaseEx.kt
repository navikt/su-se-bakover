package no.nav.su.se.bakover.common.infrastructure.persistence

import kotliquery.Row
import kotliquery.queryOf
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.toTidspunkt
import java.sql.Array
import kotlin.reflect.full.superclasses

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
        require(
            // TODO jah: Vi kan ikke ha referanser til domenet fra common. Slike custom domenesjekker hører ikke hjemme her.
            params["beregning"] == null || params["beregning"]!!::class.superclasses.any { it.simpleName?.lowercase()?.contains("beregning") == true },
        )
    }
}

/**
 * @return antall rader oppdatert
 */
fun String.oppdatering(
    params: Map<String, Any?>,
    session: Session,
): Int {
    sjekkUgyldigParameternavn(params)
    sjekkAtOppdaterInneholderWhere(this)
    sjekkTypeForBeregning(params)
    return session.run(queryOf(statement = this, paramMap = params).asUpdate)
}

/**
 * @return antall rader oppdatert
 */
fun String.insert(
    params: Map<String, Any?>,
    session: Session,
): Int {
    sjekkUgyldigParameternavn(params)
    sjekkTypeForBeregning(params)
    return session.run(queryOf(statement = this, paramMap = params).asUpdate)
}

fun <T> String.hent(
    params: Map<String, Any> = emptyMap(),
    session: Session,
    rowMapping: (Row) -> T,
): T? {
    sjekkUgyldigParameternavn(params)
    return session.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle)
}

fun <T> String.hentListe(
    params: Map<String, Any> = emptyMap(),
    session: Session,
    rowMapping: (Row) -> T,
): List<T> {
    sjekkUgyldigParameternavn(params)
    return session.run(queryOf(this, params).map { row -> rowMapping(row) }.asList)
}

fun Row.uuid30(name: String) = UUID30.fromString(string(name))
fun Row.uuid30OrNull(name: String) = stringOrNull(name)?.let { UUID30.fromString(it) }
fun Row.tidspunkt(name: String) = this.instant(name).toTidspunkt()
fun Row.tidspunktOrNull(name: String) = this.instantOrNull(name)?.toTidspunkt()

// Row.boolean(value) returnerer false dersom value er null
fun Row.booleanOrNull(name: String): Boolean? = this.anyOrNull(name)?.let { this.boolean(name) }
fun Row.periode(fraOgMed: String, tilOgMed: String): Periode {
    return Periode.create(localDate(fraOgMed), localDate(tilOgMed))
}

fun Session.inClauseWith(values: List<String>): Array =
    this.connection.underlying.createArrayOf("text", values.toTypedArray())

fun String.antall(
    params: Map<String, Any> = emptyMap(),
    session: Session,
): Long {
    sjekkUgyldigParameternavn(params)
    return session.run(queryOf(this, params).map { row -> row.long("count") }.asSingle)!!
}
