package no.nav.su.se.bakover.database.person

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.person.Fnr
import person.domain.PersonRepo
import java.util.UUID

internal class PersonPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : PersonRepo {

    override fun hentFnrForSak(sakId: UUID): List<Fnr> {
        return dbMetrics.timeQuery("hentFnrForSak") {
            sessionFactory.withSession { session ->
                """
                  SELECT
                    s.fnr søkersFnr,
                    eps_fnr epsFnr
                  FROM sak s
                    LEFT JOIN behandling b ON b.sakid = s.id
                    LEFT JOIN revurdering r ON r.sakid = s.id
                    LEFT JOIN grunnlag_bosituasjon gb ON gb.behandlingId IN (b.id, r.id)
                  WHERE s.id=:sakId
                """
                    .trimIndent()
                    .hentListe(mapOf("sakId" to sakId), session) {
                        listOfNotNull(
                            it.stringOrNull("epsFnr"),
                            it.string("søkersFnr"),
                        )
                    }
                    .flatten()
                    .distinct()
                    .map { Fnr(it) }
            }
        }
    }

    override fun hentFnrForSøknad(søknadId: UUID): List<Fnr> {
        return dbMetrics.timeQuery("hentFnrForSøknadId") {
            sessionFactory.withSession { session ->
                """
                SELECT
                    sak.fnr søkersFnr,
                    eps_fnr epsFnr
                FROM søknad
                INNER JOIN sak ON søknad.sakid = sak.id
                LEFT JOIN behandling ON behandling.sakid = sak.id
                LEFT JOIN grunnlag_bosituasjon ON grunnlag_bosituasjon.behandlingId = behandling.id
                WHERE søknad.id=:soknadId
                """
                    .trimMargin()
                    .hentListe(mapOf("soknadId" to søknadId), session) {
                        listOfNotNull(
                            it.stringOrNull("epsFnr"),
                            it.string("søkersFnr"),
                        )
                    }
                    .flatten()
                    .distinct()
                    .map { Fnr(it) }
            }
        }
    }

    override fun hentFnrForBehandling(behandlingId: UUID): List<Fnr> {
        return dbMetrics.timeQuery("hentFnrForBehandlingId") {
            sessionFactory.withSession { session ->
                """
               SELECT
                    sak.fnr søkersFnr,
                    eps_fnr epsFnr
               FROM behandling
               INNER JOIN sak ON behandling.sakid = sak.id
               LEFT JOIN grunnlag_bosituasjon ON grunnlag_bosituasjon.behandlingId = behandling.id
               WHERE behandling.id=:behandlingId
                """
                    .trimMargin()
                    .hentListe(mapOf("behandlingId" to behandlingId), session) {
                        listOfNotNull(
                            it.stringOrNull("epsFnr"),
                            it.string("søkersFnr"),
                        )
                    }
                    .flatten()
                    .distinct()
                    .map { Fnr(it) }
            }
        }
    }

    override fun hentFnrForUtbetaling(utbetalingId: UUID30): List<Fnr> {
        return dbMetrics.timeQuery("hentFnrForUtbetalingId") {
            sessionFactory.withSession { session ->
                """
               SELECT
                    sak.fnr søkersFnr,
                    eps_fnr epsFnr
               FROM utbetaling
               INNER JOIN sak on sak.id = utbetaling.sakId
               LEFT JOIN behandling ON behandling.sakid = utbetaling.sakId
               LEFT JOIN grunnlag_bosituasjon ON grunnlag_bosituasjon.behandlingId = behandling.id
               WHERE utbetaling.id=:utbetalingId
                """
                    .trimMargin()
                    .hentListe(mapOf("utbetalingId" to utbetalingId), session) {
                        listOfNotNull(
                            it.stringOrNull("epsFnr"),
                            it.string("søkersFnr"),
                        )
                    }
                    .flatten()
                    .distinct()
                    .map { Fnr(it) }
            }
        }
    }

    override fun hentFnrForRevurdering(revurderingId: UUID): List<Fnr> {
        return dbMetrics.timeQuery("hentFnrForRevurderingId") {
            sessionFactory.withSession { session ->
                """
               SELECT
                    s.fnr søkersFnr,
                    eps_fnr epsFnr
               FROM revurdering r
               INNER JOIN behandling_vedtak bv on bv.vedtakId = r.vedtakSomRevurderesId
               INNER JOIN sak s ON s.id = bv.sakId
               LEFT JOIN grunnlag_bosituasjon ON grunnlag_bosituasjon.behandlingId = r.id
               WHERE r.id=:revurderingId
                """
                    .trimMargin()
                    .hentListe(mapOf("revurderingId" to revurderingId), session) {
                        listOfNotNull(
                            it.stringOrNull("epsFnr"),
                            it.string("søkersFnr"),
                        )
                    }
                    .flatten()
                    .distinct()
                    .map { Fnr(it) }
            }
        }
    }

    override fun hentFnrForVedtak(vedtakId: UUID): List<Fnr> {
        return dbMetrics.timeQuery("hentFnrForVedtakId") {
            sessionFactory.withSession { session ->
                """
               SELECT
                    s.fnr søkersFnr,
                    eps_fnr epsFnr
                FROM behandling_vedtak bv
                LEFT JOIN sak s ON s.id = bv.sakId
                LEFT JOIN behandling b ON b.id = bv.søknadsbehandlingId
                LEFT JOIN revurdering r ON r.id = bv.revurderingId
                LEFT JOIN grunnlag_bosituasjon gb ON gb.behandlingId IN (b.id, r.id)
                WHERE bv.vedtakId = :vedtakId;
                """
                    .trimMargin()
                    .hentListe(mapOf("vedtakId" to vedtakId), session) {
                        listOfNotNull(
                            it.stringOrNull("epsFnr"),
                            it.string("søkersFnr"),
                        )
                    }
                    .flatten()
                    .distinct()
                    .map { Fnr(it) }
            }
        }
    }

    override fun hentFnrForKlage(klageId: UUID): List<Fnr> {
        return dbMetrics.timeQuery("hentFnrForKlageId") {
            sessionFactory.withSession { session ->
                """
               SELECT
                    s.fnr søkersFnr,
                    eps_fnr epsFnr
                FROM klage k
                INNER JOIN sak s ON s.id = k.sakId
                INNER JOIN behandling_vedtak bv ON bv.sakId = k.sakId
                INNER JOIN behandling b ON b.id = bv.søknadsbehandlingId
                LEFT JOIN revurdering r ON r.id = bv.revurderingId
                INNER JOIN grunnlag_bosituasjon gb ON gb.behandlingId IN (b.id, r.id)
                WHERE bv.vedtakId = :vedtakId;
                """
                    .trimMargin()
                    .hentListe(mapOf("klageId" to klageId), session) {
                        listOfNotNull(
                            it.stringOrNull("epsFnr"),
                            it.string("søkersFnr"),
                        )
                    }
                    .flatten()
                    .distinct()
                    .map { Fnr(it) }
            }
        }
    }
}
