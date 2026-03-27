package no.nav.su.se.bakover.web.services.fradragssjekken

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
                    maaned,
                    status,
                    opprettet,
                    ferdigstilt,
                    resultat,
                    feilmelding
                ) values (
                    :id,
                    :maaned,
                    :status,
                    :opprettet,
                    :ferdigstilt,
                    to_jsonb(:resultat::jsonb),
                    :feilmelding
                )
                on conflict (id) do update set
                    maaned = excluded.maaned,
                    status = excluded.status,
                    opprettet = excluded.opprettet,
                    ferdigstilt = excluded.ferdigstilt,
                    resultat = excluded.resultat,
                    feilmelding = excluded.feilmelding
            """.trimIndent().insert(
                mapOf(
                    "id" to kjoring.id,
                    "maaned" to kjoring.måned.fraOgMed,
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
                select id, maaned, status, opprettet, ferdigstilt, resultat, feilmelding
                from fradragssjekk_kjoring
                where id = :id
            """.trimIndent().hent(
                mapOf("id" to id),
                session,
            ) { row ->
                FradragssjekkKjøring(
                    id = row.uuid("id"),
                    måned = Måned.fra(row.localDate("maaned")),
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
