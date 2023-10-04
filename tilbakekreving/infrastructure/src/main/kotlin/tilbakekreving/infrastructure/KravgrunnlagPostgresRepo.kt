package tilbakekreving.infrastructure

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.toJson
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlagHendelse
import tilbakekreving.infrastructure.RåttKravgrunnlagDbJson.Companion.toJson
import tilbakekreving.infrastructure.RåttKravgrunnlagDbJson.Companion.toRåttKravgrunnlagHendelse
import java.util.UUID

val MottattKravgrunnlagHendelsestype = Hendelsestype("MOTTATT_KRAVGRUNNLAG")
val KnyttetKravgrunnlagTilSakHendelsestype = Hendelsestype("KNYTTET_KRAVGRUNNLAG_TIL_SAK")

class KravgrunnlagPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val hendelseRepo: HendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val mapper: (råttKravgrunnlag: RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>,
) : KravgrunnlagRepo {

    /**
     * TODO jah: Slett denne når vi har flyttet til egen hendelse.
     */
    override fun hentRåttÅpentKravgrunnlagForSak(
        sakId: UUID,
        sessionContext: SessionContext?,
    ): RåttKravgrunnlag? {
        // TODO jah: mottatt_kravgrunnlag er en delt tilstand, men vil på sikt eies av kravgrunnlag-delen av tilbakekreving.
        // TODO jah: Vi skal flytte fremtidige kravgrunnlag til en egen hendelse.
        return sessionContext.withOptionalSession(sessionFactory) { session ->
            """
                select 
                    kravgrunnlag 
                from 
                    revurdering_tilbakekreving 
                where 
                    tilstand = 'mottatt_kravgrunnlag' 
                    and tilbakekrevingsvedtakForsendelse is null 
                    and sakId=:sakId
                    and opprettet = (SELECT MAX(opprettet) FROM revurdering_tilbakekreving);
            """.trimIndent().hent(
                mapOf("sakId" to sakId),
                session,
            ) {
                it.stringOrNull("kravgrunnlag")?.let { RåttKravgrunnlag(it) }
            }
        }
    }

    /**
     * TODO jah: Slett denne når vi har flyttet til egen hendelse.
     */
    override fun hentKravgrunnlagForSak(sakId: UUID, sessionContext: SessionContext?): List<Kravgrunnlag> {
        return sessionContext.withOptionalSession(sessionFactory) { session ->
            """
                select 
                    kravgrunnlag 
                from 
                    revurdering_tilbakekreving 
                where 
                    sakId=:sakId;
            """.trimIndent().hentListe(
                mapOf("sakId" to sakId),
                session,
            ) {
                it.stringOrNull("kravgrunnlag")?.let { RåttKravgrunnlag(it) }
            }.filterNotNull()
        }.map {
            mapper(it).getOrElse {
                throw it
            }
        }
    }

    override fun lagreRåttKravgrunnlagHendelse(
        hendelse: RåttKravgrunnlagHendelse,
        sessionContext: SessionContext?,
    ) {
        (hendelseRepo as HendelsePostgresRepo).persisterHendelse(
            hendelse = hendelse,
            type = MottattKravgrunnlagHendelsestype,
            data = hendelse.toJson(),
            sessionContext = sessionContext,
            meta = hendelse.meta.toJson(),
        )
    }

    /**
     * Kun tenkt brukt av jobben som knytter kravgrunnlag til sak.
     * Husk og marker hendelsen som prosessert etter at den er behandlet.
     */
    override fun hentUprosesserteRåttKravgrunnlagHendelser(
        konsumentId: HendelseskonsumentId,
        sessionContext: SessionContext?,
        limit: Int,
    ): List<HendelseId> {
        return hendelsekonsumenterRepo.hentHendelseIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = MottattKravgrunnlagHendelsestype,
        )
    }

    /**
     * Kun tenkt brukt av jobben som knytter kravgrunnlag til sak.
     * Husk og marker hendelsen som prosessert etter at den er behandlet.
     */
    override fun hentRåttKravgrunnlagHendelseForHendelseId(
        hendelseId: HendelseId,
        sessionContext: SessionContext?,
    ): RåttKravgrunnlagHendelse? {
        return (hendelseRepo as HendelsePostgresRepo).hentHendelseForHendelseId(hendelseId)
            ?.toRåttKravgrunnlagHendelse()
    }

    /**
     * Denne er kun tenkt brukt av jobben som knytter kravgrunnlag til sak.
     */
    override fun lagreKravgrunnlagPåSakHendelse(hendelse: KravgrunnlagPåSakHendelse, sessionContext: SessionContext?) {
        (hendelseRepo as HendelsePostgresRepo).persisterHendelse(
            hendelse = hendelse,
            type = KnyttetKravgrunnlagTilSakHendelsestype,
            data = hendelse.toJson(),
            sessionContext = sessionContext,
        )
    }

    /**
     * Kun tenkt brukt av jobben som ferdigstiller revurdering med tilbakekreving inntil tilbakekreving ikke lenger er en del av revurdering.
     * Husk og marker hendelsen som prosessert etter at den er behandlet.
     */
    override fun hentUprosesserteKravgrunnlagKnyttetTilSakHendelser(
        konsumentId: HendelseskonsumentId,
        sessionContext: SessionContext?,
        limit: Int,
    ): List<HendelseId> {
        return hendelsekonsumenterRepo.hentHendelseIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = KnyttetKravgrunnlagTilSakHendelsestype,
        )
    }

    /**
     * Kun tenkt brukt av jobben som knytter kravgrunnlag til sak.
     * Husk og marker hendelsen som prosessert etter at den er behandlet.
     */
    override fun hentKravgrunnlagKnyttetTilSak(
        hendelseId: HendelseId,
        sessionContext: SessionContext?,
    ): KravgrunnlagPåSakHendelse? {
        return (hendelseRepo as HendelsePostgresRepo).hentHendelseForHendelseId(hendelseId)
            ?.toKravgrunnlagPåSakHendelse()
    }

    /**
     * Denne er kun tenkt brukt av SakRepo (henter kravgrunnlag på sak).
     */
    override fun hentKravgrunnlagPåSakHendelser(
        sakId: UUID,
        sessionContext: SessionContext?,
    ): List<KravgrunnlagPåSakHendelse> {
        return (hendelseRepo as HendelsePostgresRepo).hentHendelserForSakIdOgType(
            sakId = sakId,
            type = KnyttetKravgrunnlagTilSakHendelsestype,
            sessionContext = sessionContext,
        ).map {
            it.toKravgrunnlagPåSakHendelse()
        }
    }
}
