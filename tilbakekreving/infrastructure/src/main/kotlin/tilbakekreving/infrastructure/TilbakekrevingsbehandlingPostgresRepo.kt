package tilbakekreving.infrastructure

import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import tilbakekreving.domain.BrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.MånedsvurderingerTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelser
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
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
    private val sessionFactory: SessionFactory,
    private val hendelseRepo: HendelseRepo,
    private val clock: Clock,
    private val kravgrunnlagRepo: KravgrunnlagRepo,
) : TilbakekrevingsbehandlingRepo {
    override fun lagre(
        hendelse: TilbakekrevingsbehandlingHendelse,
        sessionContext: SessionContext?,
    ) {
        sessionContext.withOptionalSession(sessionFactory) {
            // TODO jah: Kanskje vi bør flytte denne til interfacet?
            (hendelseRepo as HendelsePostgresRepo).persisterSakshendelse(
                hendelse = hendelse,
                type = when (hendelse) {
                    is OpprettetTilbakekrevingsbehandlingHendelse -> OpprettTilbakekrevingsbehandlingHendelsestype
                    is MånedsvurderingerTilbakekrevingsbehandlingHendelse -> VurderMånederTilbakekrevingsbehandlingHendelsestype
                    is BrevTilbakekrevingsbehandlingHendelse -> OppdaterBrevTilbakekrevingsbehandlingHendelsestype
                    else -> throw IllegalStateException("TilbakekrevingsbehandlingPostgresRepo-lagre mangler type for ${hendelse.id}")
                },
                data = hendelse.toJson(),
                sessionContext = null,
            )
        }
    }

    override fun hentForSak(
        sakId: UUID,
        sessionContext: SessionContext?,
    ): TilbakekrevingsbehandlingHendelser {
        return sessionFactory.withSessionContext(sessionContext) { openSessionContext ->
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
                        sessionContext = openSessionContext,
                    ).map {
                        it.toTilbakekrevingsbehandlingHendelse()
                    }
            }.let {
                TilbakekrevingsbehandlingHendelser.create(
                    sakId = sakId,
                    hendelser = it,
                    clock = clock,
                    kravgrunnlagPåSak = kravgrunnlagRepo.hentKravgrunnlagPåSakHendelser(sakId, openSessionContext),
                )
            }
        }
    }
}

private fun PersistertHendelse.toTilbakekrevingsbehandlingHendelse(): TilbakekrevingsbehandlingHendelse =
    when (this.type) {
        OpprettTilbakekrevingsbehandlingHendelsestype -> OpprettTilbakekrevingsbehandlingHendelseDbJson.toDomain(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
        )

        VurderMånederTilbakekrevingsbehandlingHendelsestype -> mapToMånedsvurderingerTilbakekrevingsbehandlingHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
            tidligereHendelsesId = this.tidligereHendelseId!!,
        )

        OppdaterBrevTilbakekrevingsbehandlingHendelsestype -> mapToBrevTilbakekrevingsbehandlingHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
            tidligereHendelsesId = this.tidligereHendelseId!!,
        )

        else -> throw IllegalStateException("Ukjent tilbakekrevingsbehandlinghendelsestype")
    }

fun TilbakekrevingsbehandlingHendelse.toJson(): String {
    return when (this) {
        is OpprettetTilbakekrevingsbehandlingHendelse -> this.toJson()
        is MånedsvurderingerTilbakekrevingsbehandlingHendelse -> this.toJson()
        is BrevTilbakekrevingsbehandlingHendelse -> this.toJson()
        else -> throw IllegalStateException("TilbakekrevingsbehandlingPostgresRepo-toJson() mangler type for å mappe ${this.id}")
    }
}
