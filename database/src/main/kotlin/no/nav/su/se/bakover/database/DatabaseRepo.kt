package no.nav.su.se.bakover.database

import kotliquery.using
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource

internal class DatabaseRepo(
    private val dataSource: DataSource,
    private val uuidFactory: UUIDFactory = UUIDFactory(),
    private val clock: Clock = Clock.systemUTC()
) : ObjectRepo {
    override fun opprettSak(fnr: Fnr): Sak {
        val opprettet = now(clock)
        val sakId = UUID.randomUUID()
        val sak = Sak(
            id = sakId,
            fnr = fnr,
            opprettet = opprettet,
            oppdrag = Oppdrag(
                id = uuidFactory.newUUID30(),
                opprettet = opprettet,
                sakId = sakId
            )
        )
        """
            with inserted_sak as(insert into sak (id, fnr, opprettet) values (:sakId, :fnr, :opprettet))
            insert into oppdrag (id, opprettet, sakId) values (:oppdragId, :opprettet, :sakId)
        """.oppdatering(
            mapOf(
                "sakId" to sak.id,
                "fnr" to fnr,
                "opprettet" to sak.opprettet,
                "oppdragId" to sak.oppdrag.id
            )
        )
        return sak
    }
    private fun String.oppdatering(params: Map<String, Any?>) {
        using(sessionOf(dataSource)) {
            this.oppdatering(params, it)
        }
    }
}
