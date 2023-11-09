package tilbakekreving.infrastructure.repo.kravgrunnlag

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.toJson
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelser
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlagHendelse
import tilbakekreving.infrastructure.repo.kravgrunnlag.RåttKravgrunnlagDbJson.Companion.toJson
import tilbakekreving.infrastructure.repo.kravgrunnlag.RåttKravgrunnlagDbJson.Companion.toRåttKravgrunnlagHendelse
import java.util.UUID

val MottattKravgrunnlagHendelsestype = Hendelsestype("MOTTATT_KRAVGRUNNLAG")
val KnyttetKravgrunnlagTilSakHendelsestype = Hendelsestype("KNYTTET_KRAVGRUNNLAG_TIL_SAK")

class KravgrunnlagPostgresRepo(
    private val hendelseRepo: HendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
) : KravgrunnlagRepo {

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
            data = hendelse.toDbJson(),
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

    override fun hentKravgrunnlagPåSakHendelser(
        sakId: UUID,
        sessionContext: SessionContext?,
    ): KravgrunnlagPåSakHendelser {
        return (hendelseRepo as HendelsePostgresRepo).hentHendelserForSakIdOgType(
            sakId = sakId,
            type = KnyttetKravgrunnlagTilSakHendelsestype,
            sessionContext = sessionContext,
        ).map {
            it.toKravgrunnlagPåSakHendelse()
        }.let { KravgrunnlagPåSakHendelser(it) }
    }
}
