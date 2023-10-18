package tilbakekreving.infrastructure.repo

import dokument.domain.DokumentHendelseRepo
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import tilbakekreving.domain.BrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.ForhåndsvarsleTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.IverksattHendelse
import tilbakekreving.domain.MånedsvurderingerTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilAttesteringHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelser
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.infrastructure.repo.forhåndsvarsel.ForhåndsvarselTilbakekrevingsbehandlingDbJson
import tilbakekreving.infrastructure.repo.forhåndsvarsel.toJson
import tilbakekreving.infrastructure.repo.iverksatt.mapToTilIverksattHendelse
import tilbakekreving.infrastructure.repo.iverksatt.toJson
import tilbakekreving.infrastructure.repo.opprettet.OpprettTilbakekrevingsbehandlingHendelseDbJson
import tilbakekreving.infrastructure.repo.opprettet.toJson
import tilbakekreving.infrastructure.repo.tilAttestering.mapToTilAttesteringHendelse
import tilbakekreving.infrastructure.repo.tilAttestering.toJson
import tilbakekreving.infrastructure.repo.vedtaksbrev.mapToBrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.infrastructure.repo.vedtaksbrev.toJson
import tilbakekreving.infrastructure.repo.vurdering.mapToMånedsvurderingerTilbakekrevingsbehandlingHendelse
import tilbakekreving.infrastructure.repo.vurdering.toJson
import java.time.Clock
import java.util.UUID

val OpprettTilbakekrevingsbehandlingHendelsestype = Hendelsestype("OPPRETT_TILBAKEKREVINGSBEHANDLING")
val ForhåndsvarsleTilbakekrevingsbehandlingHendelsestype =
    Hendelsestype("FORHÅNDSVARSLER_TILBAKEKREVINGSBEHANDLING")
private val VurderMånederTilbakekrevingsbehandlingHendelsestype =
    Hendelsestype("VURDER_MÅNEDER_TILBAKEKREVINGSBEHANDLING")
private val OppdaterBrevTilbakekrevingsbehandlingHendelsestype =
    Hendelsestype("OPPDATER_BREV_TILBAKEKREVINGSBEHANDLING")
private val TilAttesteringTilbakekrevingsbehandlingHendelsestype =
    Hendelsestype("TIL_ATTESTERING_TILBAKEKREVINGSBEHANDLING")
private val UnderkjennTilbakekrevingsbehandlingHendelsestype = Hendelsestype("UNDERKJENN_TILBAKEKREVINGSBEHANDLING")
private val IverksettTilbakekrevingsbehandlingHendelsestype = Hendelsestype("IVERKSETT_TILBAKEKREVINGSBEHANDLING")
private val AvbrytTilbakekrevingsbehandlingHendelsestype = Hendelsestype("AVBRYT_TILBAKEKREVINGSBEHANDLING")

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
                is OpprettetTilbakekrevingsbehandlingHendelse -> OpprettTilbakekrevingsbehandlingHendelsestype
                is MånedsvurderingerTilbakekrevingsbehandlingHendelse -> VurderMånederTilbakekrevingsbehandlingHendelsestype
                is BrevTilbakekrevingsbehandlingHendelse -> OppdaterBrevTilbakekrevingsbehandlingHendelsestype
                is ForhåndsvarsleTilbakekrevingsbehandlingHendelse -> ForhåndsvarsleTilbakekrevingsbehandlingHendelsestype
                is TilAttesteringHendelse -> TilAttesteringTilbakekrevingsbehandlingHendelsestype
                is IverksattHendelse -> IverksettTilbakekrevingsbehandlingHendelsestype
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
                OpprettTilbakekrevingsbehandlingHendelsestype,
                ForhåndsvarsleTilbakekrevingsbehandlingHendelsestype,
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
        OpprettTilbakekrevingsbehandlingHendelsestype -> OpprettTilbakekrevingsbehandlingHendelseDbJson.toDomain(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
        )

        ForhåndsvarsleTilbakekrevingsbehandlingHendelsestype -> ForhåndsvarselTilbakekrevingsbehandlingDbJson.toDomain(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            tidligereHendelsesId = this.tidligereHendelseId!!,
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

        TilAttesteringTilbakekrevingsbehandlingHendelsestype -> mapToTilAttesteringHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
            tidligereHendelseId = this.tidligereHendelseId!!,
        )

        IverksettTilbakekrevingsbehandlingHendelsestype -> mapToTilIverksattHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
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
    }
}
