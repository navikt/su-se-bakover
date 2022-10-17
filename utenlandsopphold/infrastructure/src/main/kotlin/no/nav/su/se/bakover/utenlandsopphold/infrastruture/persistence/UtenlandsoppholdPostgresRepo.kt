package no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo.Companion.toPersistertHendelse
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.oppdater.OppdaterUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.RegistrertUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.RegistrertUtenlandsoppholdJson.Companion.toOppdaterUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.RegistrertUtenlandsoppholdJson.Companion.toRegistrerUtenlandsoppholdHendelse
import java.util.UUID

private const val RegistrerUtenlandsoppholdHendelsestype = "REGISTRER_UTENLANDSOPPHOLD"
private const val OppdaterUtenlandsoppholdHendelsestype = "OPPDATER_UTENLANDSOPPHOLD"

private val alleTyper = nonEmptyListOf(RegistrerUtenlandsoppholdHendelsestype, OppdaterUtenlandsoppholdHendelsestype)

class UtenlandsoppholdPostgresRepo(
    private val hendelseRepo: HendelsePostgresRepo,
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : UtenlandsoppholdRepo {

    override fun lagre(hendelse: RegistrerUtenlandsoppholdHendelse) {
        lagre(hendelse, RegistrerUtenlandsoppholdHendelsestype)
    }

    override fun lagre(hendelse: OppdaterUtenlandsoppholdHendelse) {
        lagre(hendelse, OppdaterUtenlandsoppholdHendelsestype)
    }

    private fun lagre(
        hendelse: UtenlandsoppholdHendelse,
        type: String,
    ) {
        hendelseRepo.persister(
            hendelse = hendelse,
            type = type,
            data = hendelse.toJson(),
        )
    }

    override fun hentSisteHendelse(sakId: UUID, utenlandsoppholdId: UUID): UtenlandsoppholdHendelse? {
        return dbMetrics.timeQuery("hentSisteHendelseforUtenlandsoppholdId") {
            sessionFactory.withSession { session ->
                """
                    select * from hendelse
                    where sakId = :sakId
                        and type IN (${alleTyper.joinToString { "'$it'" }})
                        and (data->>'utenlandsoppholdId')::uuid = :utenlandsoppholdId
                    order by versjon desc
                    limit 1
                """.trimIndent().hent(
                    params = mapOf(
                        "sakId" to sakId,
                        "utenlandsoppholdId" to utenlandsoppholdId,
                    ),
                    session = session,
                ) {
                    toPersistertHendelse(it).toUtenlandsoppholdHendelse()
                }
            }
        }
    }

    override fun defaultSessionContext(): SessionContext {
        return sessionFactory.newSessionContext()
    }

    override fun hentForSakId(sakId: UUID, sessionContext: SessionContext): List<UtenlandsoppholdHendelse> {
        return hendelseRepo.hentHendelserForSakIdOgTyper(sakId, alleTyper, sessionContext).map {
            it.toUtenlandsoppholdHendelse()
        }
    }
}

private fun PersistertHendelse.toUtenlandsoppholdHendelse(): UtenlandsoppholdHendelse = when (this.type) {
    RegistrerUtenlandsoppholdHendelsestype -> this.toRegistrerUtenlandsoppholdHendelse()
    OppdaterUtenlandsoppholdHendelsestype -> this.toOppdaterUtenlandsoppholdHendelse()
    else -> throw IllegalStateException("Ukjent utenlandsoppholdhendelsestype")
}
