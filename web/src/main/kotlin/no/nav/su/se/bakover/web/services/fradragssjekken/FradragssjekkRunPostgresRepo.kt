package no.nav.su.se.bakover.web.services.fradragssjekken

import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Måned
import java.util.UUID

internal class FradragssjekkRunPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
) {
    fun lagreKjoring(
        kjoring: FradragssjekkKjøring,
    ) {
        sessionFactory.withSession { session ->
            """
                insert into fradragssjekk_kjoring (
                    id,
                    dato,
                    dry_run,
                    status,
                    opprettet,
                    ferdigstilt,
                    oppsummering,
                    resultat,
                    feilmelding
                ) values (
                    :id,
                    :dato,
                    :dry_run,
                    :status,
                    :opprettet,
                    :ferdigstilt,
                    to_jsonb(:oppsummering::jsonb),
                    to_jsonb(:resultat::jsonb),
                    :feilmelding
                )
            """.trimIndent().insert(
                mapOf(
                    "id" to kjoring.id,
                    "dato" to kjoring.dato,
                    "dry_run" to kjoring.dryRun,
                    "status" to kjoring.status.name,
                    "opprettet" to kjoring.opprettet,
                    "ferdigstilt" to kjoring.ferdigstilt,
                    "oppsummering" to serialize(kjoring.lagOppsummering()),
                    "resultat" to serialize(kjoring.resultat),
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
                select id, dato, dry_run, status, opprettet, ferdigstilt, resultat, feilmelding
                from fradragssjekk_kjoring
                where id = :id
            """.trimIndent().hent(
                mapOf("id" to id),
                session,
            ) { row -> row.tilFradragssjekkKjoring() }
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
        return hentKjoring(kjoringId)?.resultat?.saksresultater ?: emptyList()
    }

    fun hentSaksresultaterMedEksternFeil(
        kjoringId: UUID,
    ): List<FradragssjekkSakResultat> {
        return hentSaksresultaterForKjoring(kjoringId)
            .filter { it.status == FradragssjekkSakStatus.EKSTERN_FEIL }
    }

    fun hentSjekkplanerForSakerMedEksternFeil(
        kjoringId: UUID,
    ): List<SjekkPlan> {
        return hentSaksresultaterMedEksternFeil(kjoringId).map { it.sjekkplan.tilDomain() }
    }
}

private fun Row.tilFradragssjekkKjoring(): FradragssjekkKjøring {
    return FradragssjekkKjøring(
        id = uuid("id"),
        dato = localDate("dato"),
        dryRun = boolean("dry_run"),
        status = FradragssjekkKjøringStatus.valueOf(string("status")),
        opprettet = instant("opprettet"),
        ferdigstilt = instant("ferdigstilt"),
        resultat = deserialize(string("resultat")),
        feilmelding = stringOrNull("feilmelding"),
    )
}
