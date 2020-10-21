package no.nav.su.se.bakover.database.sak

import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.sak.SakRepoInternal.hentSakInternal
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource

internal class SakPostgresRepo(
    private val dataSource: DataSource,
    private val uuidFactory: UUIDFactory = UUIDFactory(),
    private val clock: Clock = Clock.systemUTC()
) : SakRepo {
    override fun hentSak(sakId: UUID) = dataSource.withSession { hentSakInternal(sakId, it) }
    override fun hentSak(fnr: Fnr) = dataSource.withSession { hentSakInternal(fnr, it) }

    override fun opprettSak(sak: Sak) {
        dataSource.withSession { session ->
            """
            with inserted_sak as(insert into sak (id, fnr, opprettet) values (:sakId, :fnr, :opprettet))
            insert into oppdrag (id, opprettet, sakId) values (:oppdragId, :opprettet, :sakId)
        """.oppdatering(
                mapOf(
                    "sakId" to sak.id,
                    "fnr" to sak.fnr,
                    "opprettet" to sak.opprettet,
                    "oppdragId" to sak.oppdrag.id
                ),
                session
            )
        }
    }
}
