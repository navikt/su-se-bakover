package no.nav.su.se.bakover.database.person

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import java.util.UUID
import javax.sql.DataSource

internal class PersonPostgresRepo(
    private val dataSource: DataSource
) : PersonRepo {
    override fun hentFnrForSak(sakId: UUID): List<Fnr> {
        return dataSource.withSession { session ->
            """
                SELECT
                    s.fnr søkersFnr,
                    eps_fnr epsFnr
                FROM sak s
                 LEFT JOIN behandling b ON b.sakid = s.id
                 LEFT JOIN behandling_vedtak bv on bv.sakId = s.id
                 LEFT JOIN revurdering r ON r.vedtaksomrevurderesid = bv.vedtakid
                 LEFT JOIN grunnlag_bosituasjon gb ON gb.behandlingId IN (b.id, r.id)
               WHERE s.id=:sakId
|           """
                .trimMargin()
                .hentListe(mapOf("sakId" to sakId), session) {
                    listOfNotNull(
                        it.stringOrNull("epsFnr"),
                        it.string("søkersFnr")
                    )
                }
                .flatten()
                .distinct()
                .map { Fnr(it) }
        }
    }

    override fun hentFnrForSøknad(søknadId: UUID): List<Fnr> {
        return dataSource.withSession { session ->
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
                        it.string("søkersFnr")
                    )
                }
                .flatten()
                .distinct()
                .map { Fnr(it) }
        }
    }

    override fun hentFnrForBehandling(behandlingId: UUID): List<Fnr> {
        return dataSource.withSession { session ->
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
                        it.string("søkersFnr")
                    )
                }
                .flatten()
                .distinct()
                .map { Fnr(it) }
        }
    }

    override fun hentFnrForUtbetaling(utbetalingId: UUID30): List<Fnr> {
        return dataSource.withSession { session ->
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
                        it.string("søkersFnr")
                    )
                }
                .flatten()
                .distinct()
                .map { Fnr(it) }
        }
    }

    override fun hentFnrForRevurdering(revurderingId: UUID): List<Fnr> {
        return dataSource.withSession { session ->
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
                        it.string("søkersFnr")
                    )
                }
                .flatten()
                .distinct()
                .map { Fnr(it) }
        }
    }
}
