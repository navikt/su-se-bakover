package no.nav.su.se.bakover.web.services.fradragssjekken

import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Måned
import java.time.LocalDate
import java.util.UUID

internal class FradragssjekkRunPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
) {
    fun lagreKjoring(
        kjoring: FradragssjekkKjøring,
    ) {
        sessionFactory.withTransaction { session ->
            """
                insert into fradragssjekk_kjoring (
                    id,
                    dato,
                    dry_run,
                    status,
                    opprettet,
                    ferdigstilt,
                    oppsummering,
                    feilmelding
                ) values (
                    :id,
                    :dato,
                    :dryRun,
                    :status,
                    :opprettet,
                    :ferdigstilt,
                    to_jsonb(:oppsummering::jsonb),
                    :feilmelding
                )
            """.trimIndent().insert(
                mapOf(
                    "id" to kjoring.id,
                    "dato" to kjoring.dato,
                    "dryRun" to kjoring.dryRun,
                    "status" to kjoring.status.name,
                    "opprettet" to kjoring.opprettet,
                    "ferdigstilt" to kjoring.ferdigstilt,
                    "oppsummering" to serialize(kjoring.lagOppsummering()),
                    "feilmelding" to kjoring.feilmelding,
                ),
                session,
            )
        }
    }

    fun hentKjoring(
        id: UUID,
    ): FradragssjekkKjøring? {
        return sessionFactory.withSession { session ->
            """
                select id, dato, dry_run, status, opprettet, ferdigstilt, feilmelding
                from fradragssjekk_kjoring
                where id = :id
            """.trimIndent().hent(
                mapOf("id" to id),
                session,
            ) { row ->
                row.tilFradragssjekkKjoring(
                    resultat = FradragssjekkResultat(
                        saksresultater = hentSaksresultaterForKjoring(id, session),
                    ),
                )
            }
        }
    }

    fun harOrdinaerKjoringForMåned(
        måned: Måned,
    ): Boolean {
        return sessionFactory.withSession { session ->
            """
                select 1
                from fradragssjekk_kjoring
                where extract(year from dato) = :year
                  and extract(month from dato) = :month
                  and dry_run = false
                limit 1
            """.trimIndent().hent(
                mapOf(
                    "year" to måned.fraOgMed.year,
                    "month" to måned.fraOgMed.monthValue,
                ),
                session,
            ) { true } == true
        }
    }

    fun hentSaksresultaterForKjoring(
        kjoringId: UUID,
    ): List<FradragssjekkSakResultat> {
        return sessionFactory.withSession { session ->
            hentSaksresultaterForKjoring(kjoringId, session)
        }
    }

    fun hentSaksresultaterMedEksternFeil(
        kjoringId: UUID,
    ): List<FradragssjekkSakResultat> {
        return hentSaksresultaterForKjoring(kjoringId)
            .filter { it.status == FradragssjekkSakStatus.EKSTERN_FEIL }
    }

    fun lagreSaksresultater(
        saker: List<FradragssjekkSakResultat>,
        måned: Måned,
        kjøringId: UUID,
    ) {
        if (saker.isEmpty()) return

        val sql =
            """
                insert into fradragssjekk_resultat_per_kjoring (
                    kjoring_id,
                    sak_id,
                    dato,
                    opprettet,
                    resultat
                ) values (
                    :kjoringId,
                    :sakId,
                    :dato,
                    :opprettet,
                    to_jsonb(:resultat::jsonb)
                )
            """.trimIndent()

        sessionFactory.withTransaction { session ->
            val rows = saker.map { saksresultat ->
                mapOf(
                    "kjoringId" to kjøringId,
                    "sakId" to saksresultat.sakId,
                    "dato" to måned,
                    "opprettet" to LocalDate.now(),
                    "resultat" to serialize(saksresultat),
                )
            }

            session.batchPreparedNamedStatement(sql, rows)
        }
    }

    private fun hentSaksresultaterForKjoring(
        kjoringId: UUID,
        session: Session,
    ): List<FradragssjekkSakResultat> {
        return """
            select resultat
            from fradragssjekk_resultat_per_kjoring
            where kjoring_id = :kjoringId
            order by sak_id
        """.trimIndent().hentListe(
            mapOf("kjoringId" to kjoringId),
            session,
        ) { row ->
            deserialize<FradragssjekkSakResultat>(row.string("resultat"))
        }
    }
}

private fun Row.tilFradragssjekkKjoring(
    resultat: FradragssjekkResultat,
): FradragssjekkKjøring {
    return FradragssjekkKjøring(
        id = uuid("id"),
        dato = localDate("dato"),
        dryRun = boolean("dry_run"),
        status = FradragssjekkKjøringStatus.valueOf(string("status")),
        opprettet = instant("opprettet"),
        ferdigstilt = instant("ferdigstilt"),
        resultat = resultat,
        feilmelding = stringOrNull("feilmelding"),
    )
}
