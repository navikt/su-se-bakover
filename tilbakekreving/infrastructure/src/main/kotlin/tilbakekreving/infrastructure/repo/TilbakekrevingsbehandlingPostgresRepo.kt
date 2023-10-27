package tilbakekreving.infrastructure.repo

import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import tilbakekreving.domain.AvbruttHendelse
import tilbakekreving.domain.BrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.ForhåndsvarsleTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.IverksattHendelse
import tilbakekreving.domain.MånedsvurderingerTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilAttesteringHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelser
import tilbakekreving.domain.UnderkjentHendelse
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.infrastructure.repo.avbrutt.mapToTilAvbruttHendelse
import tilbakekreving.infrastructure.repo.avbrutt.toJson
import tilbakekreving.infrastructure.repo.forhåndsvarsel.ForhåndsvarselTilbakekrevingsbehandlingDbJson
import tilbakekreving.infrastructure.repo.forhåndsvarsel.toJson
import tilbakekreving.infrastructure.repo.iverksatt.mapToTilIverksattHendelse
import tilbakekreving.infrastructure.repo.iverksatt.toJson
import tilbakekreving.infrastructure.repo.opprettet.OpprettTilbakekrevingsbehandlingHendelseDbJson
import tilbakekreving.infrastructure.repo.opprettet.toJson
import tilbakekreving.infrastructure.repo.tilAttestering.mapToTilAttesteringHendelse
import tilbakekreving.infrastructure.repo.tilAttestering.toJson
import tilbakekreving.infrastructure.repo.underkjenn.mapToTilUnderkjentHendelse
import tilbakekreving.infrastructure.repo.underkjenn.toJson
import tilbakekreving.infrastructure.repo.vedtaksbrev.mapToBrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.infrastructure.repo.vedtaksbrev.toJson
import tilbakekreving.infrastructure.repo.vurdering.mapToMånedsvurderingerTilbakekrevingsbehandlingHendelse
import tilbakekreving.infrastructure.repo.vurdering.toJson
import java.time.Clock
import java.util.UUID

val OpprettetTilbakekrevingsbehandlingHendelsestype = Hendelsestype("OPPRETTET_TILBAKEKREVINGSBEHANDLING")
val ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype =
    Hendelsestype("FORHÅNDSVARSLET_TILBAKEKREVINGSBEHANDLING")
private val VurdertTilbakekrevingsbehandlingHendelsestype =
    Hendelsestype("VURDERT_TILBAKEKREVINGSBEHANDLING")
private val OppdatertVedtaksbrevTilbakekrevingsbehandlingHendelsestype =
    Hendelsestype("OPPDATERT_VEDTAKSBREV_TILBAKEKREVINGSBEHANDLING")
private val TilbakekrevingsbehandlingTilAttesteringHendelsestype =
    Hendelsestype("TILBAKEKREVINGSBEHANDLING_TIL_ATTESTERING")
private val UnderkjentTilbakekrevingsbehandlingHendelsestype = Hendelsestype("UNDERKJENT_TILBAKEKREVINGSBEHANDLING")
private val IverksattTilbakekrevingsbehandlingHendelsestype = Hendelsestype("IVERKSATT_TILBAKEKREVINGSBEHANDLING")
private val AvbruttTilbakekrevingsbehandlingHendelsestype = Hendelsestype("AVBRUTT_TILBAKEKREVINGSBEHANDLING")

class TilbakekrevingsbehandlingPostgresRepo(
    private val sessionFactory: SessionFactory,
    private val hendelseRepo: HendelseRepo,
    private val clock: Clock,
    private val kravgrunnlagRepo: KravgrunnlagRepo,
    private val oppgaveRepo: OppgaveHendelseRepo,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
) : TilbakekrevingsbehandlingRepo {
    override fun lagre(
        hendelse: TilbakekrevingsbehandlingHendelse,
        sessionContext: SessionContext?,
    ) {
        // TODO jah: Kanskje vi bør flytte denne til interfacet?
        (hendelseRepo as HendelsePostgresRepo).persisterSakshendelse(
            hendelse = hendelse,
            type = when (hendelse) {
                is OpprettetTilbakekrevingsbehandlingHendelse -> OpprettetTilbakekrevingsbehandlingHendelsestype
                is MånedsvurderingerTilbakekrevingsbehandlingHendelse -> VurdertTilbakekrevingsbehandlingHendelsestype
                is BrevTilbakekrevingsbehandlingHendelse -> OppdatertVedtaksbrevTilbakekrevingsbehandlingHendelsestype
                is ForhåndsvarsleTilbakekrevingsbehandlingHendelse -> ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype
                is TilAttesteringHendelse -> TilbakekrevingsbehandlingTilAttesteringHendelsestype
                is IverksattHendelse -> IverksattTilbakekrevingsbehandlingHendelsestype
                is AvbruttHendelse -> AvbruttTilbakekrevingsbehandlingHendelsestype
                is UnderkjentHendelse -> UnderkjentTilbakekrevingsbehandlingHendelsestype
            },
            data = hendelse.toJson(),
            sessionContext = sessionContext,
        )
    }

    override fun hentHendelse(id: HendelseId, sessionContext: SessionContext?): TilbakekrevingsbehandlingHendelse? {
        return sessionFactory.withSessionContext(sessionContext) {
            (hendelseRepo as HendelsePostgresRepo).hentHendelseForHendelseId(id, it)
        }.let {
            it?.toTilbakekrevingsbehandlingHendelse()
        }
    }

    override fun hentForSak(
        sakId: UUID,
        sessionContext: SessionContext?,
    ): TilbakekrevingsbehandlingHendelser {
        return sessionFactory.withSessionContext(sessionContext) { openSessionContext ->
            listOf(
                OpprettetTilbakekrevingsbehandlingHendelsestype,
                ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype,
                VurdertTilbakekrevingsbehandlingHendelsestype,
                OppdatertVedtaksbrevTilbakekrevingsbehandlingHendelsestype,
                TilbakekrevingsbehandlingTilAttesteringHendelsestype,
                IverksattTilbakekrevingsbehandlingHendelsestype,
                AvbruttTilbakekrevingsbehandlingHendelsestype,
                UnderkjentTilbakekrevingsbehandlingHendelsestype,
            ).flatMap {
                (hendelseRepo as HendelsePostgresRepo)
                    .hentHendelserForSakIdOgType(
                        sakId = sakId,
                        type = it,
                        sessionContext = openSessionContext,
                    ).map {
                        it.toTilbakekrevingsbehandlingHendelse()
                    }
            }.let { tilbakekrevingsHendelser ->
                TilbakekrevingsbehandlingHendelser.create(
                    sakId = sakId,
                    hendelser = tilbakekrevingsHendelser,
                    clock = clock,
                    kravgrunnlagPåSak = kravgrunnlagRepo.hentKravgrunnlagPåSakHendelser(sakId, openSessionContext),
                    oppgaveHendelser = oppgaveRepo.hentForSak(sakId, openSessionContext).filter { oppgaveHendelse ->
                        tilbakekrevingsHendelser.any { oppgaveHendelse.relaterteHendelser.contains(it.hendelseId) }
                    }.sorted(),
                    dokumentHendelser = dokumentHendelseRepo.hentForSak(sakId, openSessionContext)
                        .filter { dokumentHendelse ->
                            tilbakekrevingsHendelser.any { dokumentHendelse.relaterteHendelser.contains(it.hendelseId) }
                        }.sorted(),
                )
            }
        }
    }
}

private fun PersistertHendelse.toTilbakekrevingsbehandlingHendelse(): TilbakekrevingsbehandlingHendelse =
    when (this.type) {
        OpprettetTilbakekrevingsbehandlingHendelsestype -> OpprettTilbakekrevingsbehandlingHendelseDbJson.toDomain(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
        )

        ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype -> ForhåndsvarselTilbakekrevingsbehandlingDbJson.toDomain(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            tidligereHendelsesId = this.tidligereHendelseId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
        )

        VurdertTilbakekrevingsbehandlingHendelsestype -> mapToMånedsvurderingerTilbakekrevingsbehandlingHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
            tidligereHendelsesId = this.tidligereHendelseId!!,
        )

        OppdatertVedtaksbrevTilbakekrevingsbehandlingHendelsestype -> mapToBrevTilbakekrevingsbehandlingHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
            tidligereHendelsesId = this.tidligereHendelseId!!,
        )

        TilbakekrevingsbehandlingTilAttesteringHendelsestype -> mapToTilAttesteringHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
            tidligereHendelseId = this.tidligereHendelseId!!,
        )

        IverksattTilbakekrevingsbehandlingHendelsestype -> mapToTilIverksattHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
            tidligereHendelseId = this.tidligereHendelseId!!,
        )

        AvbruttTilbakekrevingsbehandlingHendelsestype -> mapToTilAvbruttHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = defaultHendelseMetadata(),
            tidligereHendelseId = this.tidligereHendelseId!!,
        )

        UnderkjentTilbakekrevingsbehandlingHendelsestype -> mapToTilUnderkjentHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = defaultHendelseMetadata(),
            tidligereHendelseId = this.tidligereHendelseId!!,
        )

        else -> throw IllegalStateException("Ukjent tilbakekrevingsbehandlinghendelsestype")
    }

fun TilbakekrevingsbehandlingHendelse.toJson(): String {
    return when (this) {
        is OpprettetTilbakekrevingsbehandlingHendelse -> this.toJson()
        is ForhåndsvarsleTilbakekrevingsbehandlingHendelse -> this.toJson()
        is MånedsvurderingerTilbakekrevingsbehandlingHendelse -> this.toJson()
        is BrevTilbakekrevingsbehandlingHendelse -> this.toJson()
        is TilAttesteringHendelse -> this.toJson()
        is IverksattHendelse -> this.toJson()
        is AvbruttHendelse -> this.toJson()
        is UnderkjentHendelse -> this.toJson()
    }
}
