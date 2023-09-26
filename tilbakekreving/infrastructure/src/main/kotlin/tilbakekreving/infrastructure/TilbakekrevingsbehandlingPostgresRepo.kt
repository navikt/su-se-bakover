package tilbakekreving.infrastructure

import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelser
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.opprett.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import java.time.Clock
import java.util.UUID

private val OpprettTilbakekrevingsbehandlingHendelsestype = Hendelsestype("OPPRETT_TILBAKEKREVINGSBEHANDLING")
private val VurderMånederTilbakekrevingsbehandlingHendelsestype = Hendelsestype("VURDER_MÅNEDER_TILBAKEKREVINGSBEHANDLING")
private val OppdaterBrevTilbakekrevingsbehandlingHendelsestype = Hendelsestype("OPPDATER_BREV_TILBAKEKREVINGSBEHANDLING")
private val TilAttesteringTilbakekrevingsbehandlingHendelsestype = Hendelsestype("TIL_ATTESTERING_TILBAKEKREVINGSBEHANDLING")
private val UnderkjennTilbakekrevingsbehandlingHendelsestype = Hendelsestype("UNDERKJENN_TILBAKEKREVINGSBEHANDLING")
private val IverksettTilbakekrevingsbehandlingHendelsestype = Hendelsestype("IVERKSETT_TILBAKEKREVINGSBEHANDLING")
private val AvbrytTilbakekrevingsbehandlingHendelsestype = Hendelsestype("AVBRYT_TILBAKEKREVINGSBEHANDLING")

class TilbakekrevingsbehandlingPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val hendelseRepo: HendelseRepo,
    private val clock: Clock,
    private val kravgrunnlagRepo: KravgrunnlagRepo,
) : TilbakekrevingsbehandlingRepo {
    override fun opprett(
        hendelse: OpprettetTilbakekrevingsbehandlingHendelse,
        sessionContext: SessionContext?,
    ) {
        sessionContext.withOptionalSession(sessionFactory) {
            // TODO jah: Kanskje vi bør flytte denne til interfacet?
            (hendelseRepo as HendelsePostgresRepo).persisterSakshendelse(
                hendelse = hendelse,
                type = OpprettTilbakekrevingsbehandlingHendelsestype,
                data = hendelse.toJson(),
                sessionContext = null,
            )
        }
    }

    override fun hentForSak(
        sakId: UUID,
        sessionContext: SessionContext?,
    ): TilbakekrevingsbehandlingHendelser {
        return sessionContext.withOptionalSession(sessionFactory) {
            listOf(
                OpprettTilbakekrevingsbehandlingHendelsestype,
                VurderMånederTilbakekrevingsbehandlingHendelsestype,
                OppdaterBrevTilbakekrevingsbehandlingHendelsestype,
                TilAttesteringTilbakekrevingsbehandlingHendelsestype,
                UnderkjennTilbakekrevingsbehandlingHendelsestype,
                IverksettTilbakekrevingsbehandlingHendelsestype,
                AvbrytTilbakekrevingsbehandlingHendelsestype,
            ).flatMap {
                (hendelseRepo as HendelsePostgresRepo)
                    .hentHendelserForSakIdOgType(
                        sakId = sakId,
                        type = it,
                        sessionContext = sessionContext ?: sessionFactory.newSessionContext(),
                    ).map {
                        it.toTilbakekrevingsbehandlingHendelse()
                    }
            }.let {
                TilbakekrevingsbehandlingHendelser.create(
                    sakId = sakId,
                    hendelser = it,
                    clock = clock,
                    kravgrunnlagPåSak = kravgrunnlagRepo.hentKravgrunnlagForSak(sakId),
                )
            }
        }
    }
}

private fun PersistertHendelse.toTilbakekrevingsbehandlingHendelse(): TilbakekrevingsbehandlingHendelse = when (this.type) {
    OpprettTilbakekrevingsbehandlingHendelsestype -> OpprettTilbakekrevingsbehandlingHendelseDbJson.toDomain(
        data = this.data,
        hendelseId = this.hendelseId,
        sakId = this.sakId!!,
        hendelsestidspunkt = this.hendelsestidspunkt,
        versjon = this.versjon,
        meta = this.hendelseMetadata,
    )
    else -> throw IllegalStateException("Ukjent tilbakekrevingsbehandlinghendelsestype")
}
