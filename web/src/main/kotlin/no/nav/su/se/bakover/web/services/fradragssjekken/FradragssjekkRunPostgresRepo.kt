package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.serialize
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
                    resultat,
                    feilmelding
                ) values (
                    :id,
                    :dato,
                    :dry_run,
                    :status,
                    :opprettet,
                    :ferdigstilt,
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
            ) { row ->
                FradragssjekkKjøring(
                    id = row.uuid("id"),
                    dato = row.localDate("dato"),
                    dryRun = row.boolean("dry_run"),
                    status = FradragssjekkKjøringStatus.valueOf(row.string("status")),
                    opprettet = row.instant("opprettet"),
                    ferdigstilt = row.instant("ferdigstilt"),
                    resultat = deserialize(row.string("resultat")),
                    feilmelding = row.stringOrNull("feilmelding"),
                )
            }
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
