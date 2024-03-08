package no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.toDbJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.AnnullerUtenlandsoppholdJson.Companion.toAnnullertUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.AnnullerUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.KorrigerUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.KorrigerUtenlandsoppholdJson.Companion.toKorrigerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.RegistrertUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.RegistrertUtenlandsoppholdJson.Companion.toRegistrerUtenlandsoppholdHendelse
import vilkår.utenlandsopphold.domain.UtenlandsoppholdHendelse
import vilkår.utenlandsopphold.domain.UtenlandsoppholdHendelser
import vilkår.utenlandsopphold.domain.UtenlandsoppholdRepo
import vilkår.utenlandsopphold.domain.annuller.AnnullerUtenlandsoppholdHendelse
import vilkår.utenlandsopphold.domain.korriger.KorrigerUtenlandsoppholdHendelse
import vilkår.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdHendelse
import java.time.Clock
import java.util.UUID

private val RegistrerUtenlandsoppholdHendelsestype = Hendelsestype("REGISTRER_UTENLANDSOPPHOLD")
private val KorrigerUtenlandsoppholdHendelsestype = Hendelsestype("KORRIGER_UTENLANDSOPPHOLD")
private val AnnullerUtenlandsoppholdHendelsestype = Hendelsestype("ANNULLER_UTENLANDSOPPHOLD")

class UtenlandsoppholdPostgresRepo(
    private val hendelseRepo: HendelsePostgresRepo,
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val clock: Clock,
) : UtenlandsoppholdRepo {

    override fun lagre(
        hendelse: RegistrerUtenlandsoppholdHendelse,
        meta: DefaultHendelseMetadata,
    ) {
        dbMetrics.timeQuery("persisterRegistrerUtenlandsoppholdHendelse") {
            hendelseRepo.persisterSakshendelse(
                hendelse = hendelse,
                type = RegistrerUtenlandsoppholdHendelsestype,
                data = hendelse.toJson(),
                meta = meta.toDbJson(),
            )
        }
    }

    override fun lagre(
        hendelse: KorrigerUtenlandsoppholdHendelse,
        meta: DefaultHendelseMetadata,
    ) {
        dbMetrics.timeQuery("persisterKorrigerUtenlandsoppholdHendelse") {
            hendelseRepo.persisterSakshendelse(
                hendelse = hendelse,
                type = KorrigerUtenlandsoppholdHendelsestype,
                data = hendelse.toJson(),
                meta = meta.toDbJson(),
            )
        }
    }

    override fun lagre(
        hendelse: AnnullerUtenlandsoppholdHendelse,
        meta: DefaultHendelseMetadata,
    ) {
        dbMetrics.timeQuery("persisterAnnullerUtenlandsoppholdHendelse") {
            hendelseRepo.persisterSakshendelse(
                hendelse = hendelse,
                type = AnnullerUtenlandsoppholdHendelsestype,
                data = hendelse.toJson(),
                meta = meta.toDbJson(),
            )
        }
    }

    override fun defaultSessionContext(): SessionContext {
        return sessionFactory.newSessionContext()
    }

    override fun hentForSakId(sakId: UUID, sessionContext: SessionContext): UtenlandsoppholdHendelser {
        return dbMetrics.timeQuery("hentUtenlandsoppholdHendelserForSakId") {
            // We have to open the session at this point if it is not already opened, if not, it will be closed after the first call to withSession
            sessionContext.withSession {
                listOf(
                    RegistrerUtenlandsoppholdHendelsestype,
                    KorrigerUtenlandsoppholdHendelsestype,
                    AnnullerUtenlandsoppholdHendelsestype,
                ).flatMap {
                    hendelseRepo.hentHendelserForSakIdOgType(sakId, it, sessionContext)
                }.map {
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
}

private fun PersistertHendelse.toUtenlandsoppholdHendelse(): UtenlandsoppholdHendelse = when (this.type) {
    RegistrerUtenlandsoppholdHendelsestype -> this.toRegistrerUtenlandsoppholdHendelse()
    KorrigerUtenlandsoppholdHendelsestype -> this.toKorrigerUtenlandsoppholdHendelse()
    AnnullerUtenlandsoppholdHendelsestype -> this.toAnnullertUtenlandsoppholdHendelse()
    else -> throw IllegalStateException("Ukjent utenlandsoppholdhendelsestype")
}
