package no.nav.su.se.bakover.database.person

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import java.util.UUID
import javax.sql.DataSource

internal class PersonPostgresRepo(
    private val dataSource: DataSource
) : PersonRepo {
    override fun hentFnrForSak(sakId: UUID): Fnr? {
        return dataSource.withSession { session ->
            """
               SELECT fnr
               FROM sak
               WHERE id=:sakId
|           """
                .trimMargin()
                .hent(mapOf("sakId" to sakId), session) {
                    Fnr(it.string("fnr"))
                }
        }
    }

    override fun hentFnrForSøknad(søknadId: UUID): Fnr? {
        return dataSource.withSession { session ->
            """
                SELECT sak.fnr
                FROM søknad
                INNER JOIN sak ON søknad.sakid = sak.id
                WHERE søknad.id=:soknadId
            """
                .trimMargin()
                .hent(mapOf("soknadId" to søknadId), session) {
                    Fnr(it.string("fnr"))
                }
        }
    }

    override fun hentFnrForBehandling(behandlingId: UUID): Fnr? {
        return dataSource.withSession { session ->
            """
               SELECT s.fnr
               FROM behandling b
               INNER JOIN sak s ON b.sakid = s.id
               WHERE b.id=:behandlingId
            """
                .trimMargin()
                .hent(mapOf("behandlingId" to behandlingId), session) {
                    Fnr(it.string("fnr"))
                }
        }
    }

    override fun hentFnrForUtbetaling(utbetalingId: UUID30): Fnr? {
        return dataSource.withSession { session ->
            """
               SELECT s.fnr
               FROM utbetaling u
               INNER JOIN oppdrag o ON u.oppdragid = o.id
               INNER JOIN sak s ON o.sakid = s.id
               WHERE u.id=:utbetalingId
            """
                .trimMargin()
                .hent(mapOf("utbetalingId" to utbetalingId), session) {
                    Fnr(it.string("fnr"))
                }
        }
    }
}
