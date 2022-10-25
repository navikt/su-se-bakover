package no.nav.su.se.bakover.database.sak

import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.database.sak.RegistrertSakHendelseDatabaseJson.Companion.toSakOpprettetHendelseDatabaseJson
import no.nav.su.se.bakover.domain.sak.RegistrerSakRepo
import no.nav.su.se.bakover.domain.sak.SakRegistrertHendelse
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo

private const val SakRegistrertHendelsestype = "SAK_REGISTRERT"

class RegistrerSakPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val hendelseRepo: HendelsePostgresRepo,
) : RegistrerSakRepo {

    override fun persister(hendelse: SakRegistrertHendelse) {
        return dbMetrics.timeQuery("persisterSakRegistrertHendelse") {
            sessionFactory.withTransactionContext { context ->
                context.withTransaction { tx ->
                    """
                    insert into sak (id, fnr, opprettet, type, saksnummer) values (:sakId, :fnr, :opprettet, :type, :saksnummer)
                    """.insert(
                        mapOf(
                            "sakId" to hendelse.sakId,
                            "fnr" to hendelse.fnr,
                            "opprettet" to hendelse.hendelsestidspunkt,
                            "type" to hendelse.sakstype,
                            "saksnummer" to hendelse.saksnummer,
                        ),
                        tx,
                    )
                }
                hendelseRepo.persister(
                    hendelse = hendelse,
                    type = SakRegistrertHendelsestype,
                    data = hendelse.toSakOpprettetHendelseDatabaseJson(),
                    sessionContext = context,
                )
            }
        }
    }
}
