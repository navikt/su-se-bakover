package no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdHendelser
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdRepo
import no.nav.su.se.bakover.utenlandsopphold.domain.annuller.AnnullerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.korriger.KorrigerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.AnnullerUtenlandsoppholdJson.Companion.toAnnullertUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.AnnullerUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.KorrigerUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.KorrigerUtenlandsoppholdJson.Companion.toKorrigerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.RegistrertUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.RegistrertUtenlandsoppholdJson.Companion.toRegistrerUtenlandsoppholdHendelse
import java.time.Clock
import java.util.UUID

private const val RegistrerUtenlandsoppholdHendelsestype = "REGISTRER_UTENLANDSOPPHOLD"
private const val KorrigerUtenlandsoppholdHendelsestype = "KORRIGER_UTENLANDSOPPHOLD"
private const val AnnullerUtenlandsoppholdHendelsestype = "ANNULLER_UTENLANDSOPPHOLD"

private val alleTyper = nonEmptyListOf(
    RegistrerUtenlandsoppholdHendelsestype,
    KorrigerUtenlandsoppholdHendelsestype,
    AnnullerUtenlandsoppholdHendelsestype,
)

class UtenlandsoppholdPostgresRepo(
    private val hendelseRepo: HendelsePostgresRepo,
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val clock: Clock,
) : UtenlandsoppholdRepo {

    override fun lagre(hendelse: RegistrerUtenlandsoppholdHendelse) {
        dbMetrics.timeQuery("persisterRegistrerUtenlandsoppholdHendelse") {
            hendelseRepo.persister(hendelse, RegistrerUtenlandsoppholdHendelsestype, hendelse.toJson())
        }
    }

    override fun lagre(hendelse: KorrigerUtenlandsoppholdHendelse) {
        dbMetrics.timeQuery("persisterKorrigerUtenlandsoppholdHendelse") {
            hendelseRepo.persister(hendelse, KorrigerUtenlandsoppholdHendelsestype, hendelse.toJson())
        }
    }

    override fun lagre(hendelse: AnnullerUtenlandsoppholdHendelse) {
        dbMetrics.timeQuery("persisterAnnullerUtenlandsoppholdHendelse") {
            hendelseRepo.persister(hendelse, AnnullerUtenlandsoppholdHendelsestype, hendelse.toJson())
        }
    }

    override fun defaultSessionContext(): SessionContext {
        return sessionFactory.newSessionContext()
    }

    override fun hentForSakId(sakId: UUID, sessionContext: SessionContext): UtenlandsoppholdHendelser {
        return dbMetrics.timeQuery("hentUtenlandsoppholdHendelserForSakId") {
            hendelseRepo.hentHendelserForSakIdOgTyper(sakId, alleTyper, sessionContext).map {
                it.toUtenlandsoppholdHendelse()
            }.let {
                UtenlandsoppholdHendelser.create(
                    sakId = sakId,
                    clock = clock,
                    hendelser = it,
                )
            }
        }
    }
}

private fun PersistertHendelse.toUtenlandsoppholdHendelse(): UtenlandsoppholdHendelse = when (this.type) {
    RegistrerUtenlandsoppholdHendelsestype -> this.toRegistrerUtenlandsoppholdHendelse()
    KorrigerUtenlandsoppholdHendelsestype -> this.toKorrigerUtenlandsoppholdHendelse()
    AnnullerUtenlandsoppholdHendelsestype -> this.toAnnullertUtenlandsoppholdHendelse()
    else -> throw IllegalStateException("Ukjent utenlandsoppholdhendelsestype")
}
