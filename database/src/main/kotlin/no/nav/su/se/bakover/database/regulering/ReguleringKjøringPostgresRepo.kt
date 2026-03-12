package no.nav.su.se.bakover.database.regulering

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringRepo

class ReguleringKjøringPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : ReguleringKjøringRepo {

    override fun lagre(oppsummering: ReguleringKjøring) {
        dbMetrics.timeQuery("lagreReguleringKjøring") {
            sessionFactory.withSession { session ->
                """
                    insert into reguleringskjøring (id, aar, type, start_tid, antall_prosesserte_saker, antall_reguleringer_laget, antall_kunne_ikke_opprettes) values (:id, :aar, :type, :start_tid, :antall_prosesserte_saker, :antall_reguleringer_laget, :antall_kunne_ikke_opprettes)
                """.trimIndent().insert(
                    mapOf(
                        "id" to oppsummering.id,
                        "aar" to oppsummering.aar,
                        "type" to oppsummering.type,
                        "start_tid" to oppsummering.startTid,
                        "antall_prosesserte_saker" to oppsummering.antallProsesserteSaker,
                        "antall_reguleringer_laget" to oppsummering.antallReguleringerLaget,
                        "antall_kunne_ikke_opprettes" to oppsummering.antallKunneIkkeOpprettes,
                    ),
                    session,
                )
            }
        }
    }
}
