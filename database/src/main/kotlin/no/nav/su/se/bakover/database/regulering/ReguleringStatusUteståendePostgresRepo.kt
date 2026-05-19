package no.nav.su.se.bakover.database.regulering

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.domain.regulering.ReguleringStatus
import no.nav.su.se.bakover.domain.regulering.ReguleringStatusUteståendeRepo
import java.util.UUID

class ReguleringStatusUteståendePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : ReguleringStatusUteståendeRepo {
    override fun hent(): List<ReguleringStatus> {
        TODO("Not yet implemented")
    }

    override fun hentPågående(): List<ReguleringStatus> {
        TODO("Not yet implemented")
    }

    override fun lagreOppstartet(): UUID {
        TODO("Not yet implemented")
    }

    override fun lagreProdusert(
        idPågående: UUID,
        reguleringStatus: ReguleringStatus,
    ) {
        TODO("Not yet implemented")
    }

    override fun lagreFeilet(idPågående: UUID) {
        TODO("Not yet implemented")
    }
}
