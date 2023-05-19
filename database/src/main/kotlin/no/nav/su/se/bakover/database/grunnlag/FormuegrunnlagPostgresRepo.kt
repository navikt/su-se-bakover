package no.nav.su.se.bakover.database.grunnlag

import arrow.core.getOrElse
import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.serializeNullable
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import java.util.UUID

internal class FormuegrunnlagPostgresRepo(
    private val dbMetrics: DbMetrics,
) {
    internal fun lagreFormuegrunnlag(
        behandlingId: UUID,
        formuegrunnlag: List<Formuegrunnlag>,
        tx: TransactionalSession,
    ) {
        dbMetrics.timeQuery("lagreFormuegrunnlag") {
            slettForBehandlingId(behandlingId, tx)
            formuegrunnlag.forEach {
                lagre(it, behandlingId, tx)
            }
        }
    }

    internal fun hentFormuegrunnlag(formuegrunnlagId: UUID, session: Session): Formuegrunnlag? {
        return dbMetrics.timeQuery("hentFormuegrunnlag") {
            """
                select * from grunnlag_formue where id = :id
            """.trimIndent()
                .hent(
                    mapOf(
                        "id" to formuegrunnlagId,
                    ),
                    session,
                ) {
                    it.toFormuegrunnlag()
                }
        }
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
            delete from grunnlag_formue where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    private fun Row.toFormuegrunnlag(): Formuegrunnlag {
        return Formuegrunnlag.fromPersistence(
            id = uuid("id"),
            periode = Periode.create(fraOgMed = localDate("fraOgMed"), tilOgMed = localDate("tilOgMed")),
            opprettet = tidspunkt("opprettet"),
            epsFormue = stringOrNull("epsFormue")?.let { deserialize<FormueverdierJson?>(it)?.toDomain() },
            søkersFormue = deserialize<FormueverdierJson>(string("søkerFormue")).toDomain(),
        )
    }

    private fun lagre(formuegrunnlag: Formuegrunnlag, behandlingId: UUID, tx: TransactionalSession) {
        """
            insert into grunnlag_formue
            (
                id,
                opprettet,
                behandlingId,
                fraOgMed,
                tilOgMed,
                epsFormue,
                søkerFormue
            ) values
            (
                :id,
                :opprettet,
                :behandlingId,
                :fraOgMed,
                :tilOgMed,
                to_jsonb(:epsFormue::json),
                to_jsonb(:sokerFormue::json)
            )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to formuegrunnlag.id,
                    "opprettet" to formuegrunnlag.opprettet,
                    "behandlingId" to behandlingId,
                    "fraOgMed" to formuegrunnlag.periode.fraOgMed,
                    "tilOgMed" to formuegrunnlag.periode.tilOgMed,
                    "epsFormue" to serializeNullable(formuegrunnlag.epsFormue?.toJson()),
                    "sokerFormue" to serialize(formuegrunnlag.søkersFormue.toJson()),
                ),
                tx,
            )
    }
}

/**
 * Blir serialisert/deserialsert som json i databasen
 */
private data class FormueverdierJson(
    val verdiIkkePrimærbolig: Int,
    val verdiEiendommer: Int,
    val verdiKjøretøy: Int,
    val innskudd: Int,
    val verdipapir: Int,
    val pengerSkyldt: Int,
    val kontanter: Int,
    val depositumskonto: Int,
) {
    fun toDomain(): Formuegrunnlag.Verdier {
        return Formuegrunnlag.Verdier.tryCreate(
            verdiIkkePrimærbolig = verdiIkkePrimærbolig,
            verdiEiendommer = verdiEiendommer,
            verdiKjøretøy = verdiKjøretøy,
            innskudd = innskudd,
            verdipapir = verdipapir,
            pengerSkyldt = pengerSkyldt,
            kontanter = kontanter,
            depositumskonto = depositumskonto,
        ).getOrElse {
            throw IllegalArgumentException(it.toString())
        }
    }
}

private fun Formuegrunnlag.Verdier.toJson(): FormueverdierJson {
    return FormueverdierJson(
        verdiIkkePrimærbolig = verdiIkkePrimærbolig,
        verdiEiendommer = verdiEiendommer,
        verdiKjøretøy = verdiKjøretøy,
        innskudd = innskudd,
        verdipapir = verdipapir,
        pengerSkyldt = pengerSkyldt,
        kontanter = kontanter,
        depositumskonto = depositumskonto,
    )
}
