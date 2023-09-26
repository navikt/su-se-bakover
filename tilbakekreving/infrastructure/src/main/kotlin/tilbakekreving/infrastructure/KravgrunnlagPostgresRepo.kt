package tilbakekreving.infrastructure

import arrow.core.Either
import arrow.core.getOrElse
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

    override fun hentRåttÅpentKravgrunnlagForSak(sakId: UUID): RåttKravgrunnlag? {
        // TODO jah: mottatt_kravgrunnlag er en delt tilstand, men vil på sikt eies av kravgrunnlag-delen av tilbakekreving.
        // TODO jah: Vi skal flytte fremtidige kravgrunnlag til en egen hendelse.
        return sessionFactory.withSession { session ->
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

//    override fun hentRåttKravgrunnlag(id: String): RåttKravgrunnlag? {
//        return sessionFactory.withSession { session ->
//            """
//                select
//                    kravgrunnlag
//                from
//                    revurdering_tilbakekreving
//            """.trimIndent().hentListe(
//                emptyMap(),
//                session,
//            ) {
//                it.stringOrNull("kravgrunnlag")?.let { RåttKravgrunnlag(it) }
//            }
//        }.firstOrNull {
//            if (it == null) {
//                false
//            } else {
//                val kravgrunnlag = mapper(it).getOrElse {
//                    throw it
//                }
//                kravgrunnlag.kravgrunnlagId == id
//            }
//        }
//    }

    override fun hentKravgrunnlagForSak(sakId: UUID): List<Kravgrunnlag> {
        return sessionFactory.withSession { session ->
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

    override fun lagreKravgrunnlagPåSakHendelse(hendelse: KravgrunnlagPåSakHendelse, sessionContext: SessionContext?) {
        (hendelseRepo as HendelsePostgresRepo).persisterHendelse(
            hendelse = hendelse,
            type = KnyttetKravgrunnlagTilSakHendelsestype,
            data = hendelse.toJson(),
            sessionContext = sessionContext,
        )
    }

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
