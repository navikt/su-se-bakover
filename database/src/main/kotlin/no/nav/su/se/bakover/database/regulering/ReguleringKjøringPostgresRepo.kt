package no.nav.su.se.bakover.database.regulering

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringRepo
import java.util.UUID

class ReguleringKjøringPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : ReguleringKjøringRepo {

    override fun lagre(oppsummering: ReguleringKjøring) {
        dbMetrics.timeQuery("lagreReguleringKjøring") {
            sessionFactory.withSession { session ->
                """
                    insert into reguleringskjøring (
                    id, 
                    aar, 
                    type, 
                    start_tid, 
                    antall_prosesserte_saker, 
                    antall_reguleringer_laget, 
                    antall_kunne_ikke_opprettes,
                    arsaker_regulering_ikke_opprettet,
                    antall_automatiske_reguleringer,
                    antall_supplement_reguleringer,
                    antall_reguleringer_manuell_behandling,
                    arsaker_manuell_behandling) 
                    values (
                    :id, 
                    :aar, 
                    :type, 
                    :start_tid, 
                    :antall_prosesserte_saker, 
                    :antall_reguleringer_laget, 
                    :antall_kunne_ikke_opprettes,
                    :arsaker_regulering_ikke_opprettet,
                    :antall_automatiske_reguleringer,
                    :antall_supplement_reguleringer,
                    :antall_reguleringer_manuell_behandling,
                    :arsaker_manuell_behandling
                    )
                """.trimIndent().insert(
                    mapOf(
                        "id" to oppsummering.id,
                        "aar" to oppsummering.aar,
                        "type" to oppsummering.type,
                        "start_tid" to oppsummering.startTid,
                        "antall_prosesserte_saker" to oppsummering.antallProsesserteSaker,
                        "antall_reguleringer_laget" to oppsummering.antallReguleringerLaget,
                        "antall_kunne_ikke_opprettes" to oppsummering.antallKunneIkkeOpprettes,
                        "arsaker_regulering_ikke_opprettet" to oppsummering.arsakerReguleringIkkeOpprettet,
                        "antall_automatiske_reguleringer" to oppsummering.antallAutomatiskeReguleringer,
                        "antall_supplement_reguleringer" to oppsummering.antallSupplementReguleringer,
                        "antall_reguleringer_manuell_behandling" to oppsummering.antallReguleringerManuellBehandling,
                        "arsaker_manuell_behandling" to oppsummering.arsakerManuellBehandling,
                    ),
                    session,
                )
            }
        }
    }

    override fun hent(id: UUID): ReguleringKjøring? {
        return sessionFactory.withSession { session ->
            """
                select * from reguleringskjøring where id = :id
            """.trimIndent().hent(
                params = mapOf("id" to id),
                session = session,
            ) { row ->
                ReguleringKjøring(
                    id = row.uuid("id"),
                    aar = row.int("aar"),
                    type = row.string("type"),
                    startTid = row.localDateTime("start_tid"),
                    antallProsesserteSaker = row.int("antall_prosesserte_saker"),
                    antallReguleringerLaget = row.int("antall_reguleringer_laget"),
                    antallKunneIkkeOpprettes = row.int("antall_kunne_ikke_opprettes"),
                    arsakerReguleringIkkeOpprettet = row.string("arsaker_regulering_ikke_opprettet"),
                    antallAutomatiskeReguleringer = row.int("antall_automatiske_reguleringer"),
                    antallSupplementReguleringer = row.int("antall_supplement_reguleringer"),
                    antallReguleringerManuellBehandling = row.int("antall_reguleringer_manuell_behandling"),
                    arsakerManuellBehandling = row.string("arsaker_manuell_behandling"),
                )
            }
        }
    }
}
