package tilbakekreving.infrastructure.repo

import arrow.core.Tuple5
import dokument.domain.DokumentHendelser
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.toDbJson
import tilbakekreving.domain.AvbruttHendelse
import tilbakekreving.domain.BrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.ForhåndsvarsleTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.IverksattHendelse
import tilbakekreving.domain.NotatTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.OppdatertKravgrunnlagPåTilbakekrevingHendelse
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilAttesteringHendelse
import tilbakekreving.domain.TilbakekrevingbehandlingsSerie
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelser
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.UnderkjentHendelse
import tilbakekreving.domain.VurdertTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.iverksettelse.IverksattHendelseMetadata
import tilbakekreving.domain.kravgrunnlag.repo.KravgrunnlagRepo
import tilbakekreving.infrastructure.repo.avbrutt.mapToTilAvbruttHendelse
import tilbakekreving.infrastructure.repo.avbrutt.toJson
import tilbakekreving.infrastructure.repo.forhåndsvarsel.ForhåndsvarselTilbakekrevingsbehandlingDbJson
import tilbakekreving.infrastructure.repo.forhåndsvarsel.toJson
import tilbakekreving.infrastructure.repo.iverksatt.mapToTilIverksattHendelse
import tilbakekreving.infrastructure.repo.iverksatt.toDbJson
import tilbakekreving.infrastructure.repo.iverksatt.toJson
import tilbakekreving.infrastructure.repo.notat.mapTilNotatHendelse
import tilbakekreving.infrastructure.repo.notat.toJson
import tilbakekreving.infrastructure.repo.oppdatertKravgrunnlag.mapTilOppdatertKravgrunnlagPåTilbakekrevingHendelse
import tilbakekreving.infrastructure.repo.oppdatertKravgrunnlag.toJson
import tilbakekreving.infrastructure.repo.opprettet.OpprettTilbakekrevingsbehandlingHendelseDbJson
import tilbakekreving.infrastructure.repo.opprettet.toJson
import tilbakekreving.infrastructure.repo.tilAttestering.mapToTilAttesteringHendelse
import tilbakekreving.infrastructure.repo.tilAttestering.toJson
import tilbakekreving.infrastructure.repo.underkjenn.mapToTilUnderkjentHendelse
import tilbakekreving.infrastructure.repo.underkjenn.toJson
import tilbakekreving.infrastructure.repo.vedtaksbrev.mapToBrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.infrastructure.repo.vedtaksbrev.toJson
import tilbakekreving.infrastructure.repo.vurdering.mapToVurdertTilbakekrevingsbehandlingHendelse
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
val TilbakekrevingsbehandlingTilAttesteringHendelsestype =
    Hendelsestype("TILBAKEKREVINGSBEHANDLING_TIL_ATTESTERING")
val UnderkjentTilbakekrevingsbehandlingHendelsestype = Hendelsestype("UNDERKJENT_TILBAKEKREVINGSBEHANDLING")
val IverksattTilbakekrevingsbehandlingHendelsestype = Hendelsestype("IVERKSATT_TILBAKEKREVINGSBEHANDLING")
val AvbruttTilbakekrevingsbehandlingHendelsestype = Hendelsestype("AVBRUTT_TILBAKEKREVINGSBEHANDLING")
val OppdatertKravgrunnlagPåTilbakekrevingHendelse = Hendelsestype("OPPDATERT_KRAVGRUNNLAG")
val NotatTilbakekrevingsbehandlingHendelsestype = Hendelsestype("NOTAT_TILBAKEKREVINGSBEHANDLING")

class TilbakekrevingsbehandlingPostgresRepo(
    private val sessionFactory: SessionFactory,
    private val hendelseRepo: HendelseRepo,
    private val clock: Clock,
    private val kravgrunnlagRepo: KravgrunnlagRepo,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
) : TilbakekrevingsbehandlingRepo {

    override fun lagre(
        hendelse: TilbakekrevingsbehandlingHendelse,
        meta: DefaultHendelseMetadata,
        sessionContext: SessionContext?,
    ) {
        lagre(
            hendelse,
            meta.toDbJson(),
            sessionContext,
        )
    }

    override fun lagreIverksattTilbakekrevingshendelse(
        hendelse: IverksattHendelse,
        meta: IverksattHendelseMetadata,
        sessionContext: SessionContext?,
    ) {
        lagre(
            hendelse,
            meta.toDbJson(),
            sessionContext,
        )
    }

    private fun lagre(
        hendelse: TilbakekrevingsbehandlingHendelse,
        meta: String,
        sessionContext: SessionContext?,
    ) {
        // TODO jah: Kanskje vi bør flytte denne til interfacet?
        (hendelseRepo as HendelsePostgresRepo).persisterSakshendelse(
            hendelse = hendelse,
            type = when (hendelse) {
                is OpprettetTilbakekrevingsbehandlingHendelse -> OpprettetTilbakekrevingsbehandlingHendelsestype
                is VurdertTilbakekrevingsbehandlingHendelse -> VurdertTilbakekrevingsbehandlingHendelsestype
                is BrevTilbakekrevingsbehandlingHendelse -> OppdatertVedtaksbrevTilbakekrevingsbehandlingHendelsestype
                is ForhåndsvarsleTilbakekrevingsbehandlingHendelse -> ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype
                is TilAttesteringHendelse -> TilbakekrevingsbehandlingTilAttesteringHendelsestype
                is IverksattHendelse -> IverksattTilbakekrevingsbehandlingHendelsestype
                is AvbruttHendelse -> AvbruttTilbakekrevingsbehandlingHendelsestype
                is UnderkjentHendelse -> UnderkjentTilbakekrevingsbehandlingHendelsestype
                is OppdatertKravgrunnlagPåTilbakekrevingHendelse -> OppdatertKravgrunnlagPåTilbakekrevingHendelse
                is NotatTilbakekrevingsbehandlingHendelse -> NotatTilbakekrevingsbehandlingHendelsestype
            },
            data = hendelse.toJson(),
            sessionContext = sessionContext,
            meta = meta,
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
                OppdatertKravgrunnlagPåTilbakekrevingHendelse,
                NotatTilbakekrevingsbehandlingHendelsestype,
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
                    dokumentHendelser = dokumentHendelseRepo.hentForSak(sakId, openSessionContext)
                        .filter { dokumentHendelseSerie ->
                            tilbakekrevingsHendelser.any { dokumentHendelseSerie.relatertHendelse == it.hendelseId }
                        }.let {
                            DokumentHendelser(sakId = sakId, serier = it)
                        },
                )
            }
        }
    }

    override fun hentBehandlingsSerieFor(
        hendelse: TilbakekrevingsbehandlingHendelse,
        sessionContext: SessionContext?,
    ): TilbakekrevingbehandlingsSerie {
        return sessionFactory.withSessionContext(sessionContext) {
            hentForSak(sakId = hendelse.sakId, sessionContext = it).hentSerieFor(hendelse.id)
        }
    }

    override fun hentÅpneBehandlingssammendrag(sessionContext: SessionContext?): List<Behandlingssammendrag> {
        val hendelserGruppertPåBehandlingsId = hentBehandlingerForSammendrag(sessionContext).groupBy { it.fourth }

        val gruppertKunÅpneHendelser = hendelserGruppertPåBehandlingsId.entries.mapNotNull { entry ->
            val behandlingStartet =
                entry.value.single { it.second == OpprettetTilbakekrevingsbehandlingHendelsestype }.fifth

            // versjonen på hendelsen må være den siste hendelsen i serien for å vite at det ikke er hendelser etter som kan ha avsluttet serien
            val currentState =
                entry.value.singleOrNull { it.second.erÅpen() && it.first == entry.value.maxOf { it.first } }
                    ?: return@mapNotNull null

            Tuple5(currentState.first, currentState.second, currentState.third, currentState.fourth, behandlingStartet)
        }

        return gruppertKunÅpneHendelser.toBehandlingssammendrag()
    }

    override fun hentFerdigeBehandlingssamendrag(sessionContext: SessionContext?): List<Behandlingssammendrag> {
        val hendelserGruppertPåBehandlingsId = hentBehandlingerForSammendrag(sessionContext).groupBy { it.fourth }

        val gruppertKunÅpneHendelser = hendelserGruppertPåBehandlingsId.entries.mapNotNull { entry ->
            val behandlingStartet =
                entry.value.single { it.second == OpprettetTilbakekrevingsbehandlingHendelsestype }.fifth
            // versjonen på hendelsen må være den siste hendelsen i serien for å vite at det ikke er hendelser etter som kan ha avsluttet serien
            val currentState =
                entry.value.singleOrNull { !it.second.erÅpen() && it.first == entry.value.maxOf { it.first } }
                    ?: return@mapNotNull null

            Tuple5(currentState.first, currentState.second, currentState.third, currentState.fourth, behandlingStartet)
        }

        return gruppertKunÅpneHendelser.toBehandlingssammendrag()
    }

    private fun hentBehandlingerForSammendrag(sessionContext: SessionContext?): List<Tuple5<Long, Hendelsestype, Saksnummer, TilbakekrevingsbehandlingId, Tidspunkt>> {
        return sessionContext.withOptionalSession(sessionFactory) {
            """
                select 
                    versjon, 
                    h.type, 
                    s.saksnummer, 
                    data ->> 'behandlingsId' as behandlingsId,
                    hendelsestidspunkt
                from hendelse h
                join sak s on h.sakid = s.id
                where data ->> 'behandlingsId' is not null;
            """.trimIndent().hentListe(emptyMap(), it) {
                Tuple5(
                    it.long("versjon"),
                    it.string("type").toTilbakekrevingHendelsestype(),
                    Saksnummer(it.long("saksnummer")),
                    TilbakekrevingsbehandlingId(UUID.fromString(it.string("behandlingsId"))),
                    it.tidspunkt("hendelsestidspunkt"),
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
        )

        ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype -> ForhåndsvarselTilbakekrevingsbehandlingDbJson.toDomain(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            tidligereHendelsesId = this.tidligereHendelseId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
        )

        VurdertTilbakekrevingsbehandlingHendelsestype -> mapToVurdertTilbakekrevingsbehandlingHendelse()

        OppdatertVedtaksbrevTilbakekrevingsbehandlingHendelsestype -> mapToBrevTilbakekrevingsbehandlingHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            tidligereHendelsesId = this.tidligereHendelseId!!,
        )

        TilbakekrevingsbehandlingTilAttesteringHendelsestype -> mapToTilAttesteringHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            tidligereHendelseId = this.tidligereHendelseId!!,
        )

        IverksattTilbakekrevingsbehandlingHendelsestype -> this.mapToTilIverksattHendelse()

        AvbruttTilbakekrevingsbehandlingHendelsestype -> mapToTilAvbruttHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            tidligereHendelseId = this.tidligereHendelseId!!,
        )

        UnderkjentTilbakekrevingsbehandlingHendelsestype -> mapToTilUnderkjentHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            tidligereHendelseId = this.tidligereHendelseId!!,
        )

        OppdatertKravgrunnlagPåTilbakekrevingHendelse -> mapTilOppdatertKravgrunnlagPåTilbakekrevingHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            tidligereHendelsesId = this.tidligereHendelseId!!,
        )

        NotatTilbakekrevingsbehandlingHendelsestype -> mapTilNotatHendelse(
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            tidligereHendelsesId = this.tidligereHendelseId!!,
        )

        else -> throw IllegalStateException("Ukjent tilbakekrevingsbehandlinghendelsestype")
    }

fun TilbakekrevingsbehandlingHendelse.toJson(): String {
    return when (this) {
        is OpprettetTilbakekrevingsbehandlingHendelse -> this.toJson()
        is ForhåndsvarsleTilbakekrevingsbehandlingHendelse -> this.toJson()
        is VurdertTilbakekrevingsbehandlingHendelse -> this.toJson()
        is BrevTilbakekrevingsbehandlingHendelse -> this.toJson()
        is TilAttesteringHendelse -> this.toJson()
        is IverksattHendelse -> this.toJson()
        is AvbruttHendelse -> this.toJson()
        is UnderkjentHendelse -> this.toJson()
        is OppdatertKravgrunnlagPåTilbakekrevingHendelse -> this.toJson()
        is NotatTilbakekrevingsbehandlingHendelse -> this.toJson()
    }
}

internal fun String.toTilbakekrevingHendelsestype(): Hendelsestype = when (this) {
    "OPPRETTET_TILBAKEKREVINGSBEHANDLING" -> OpprettetTilbakekrevingsbehandlingHendelsestype
    "FORHÅNDSVARSLET_TILBAKEKREVINGSBEHANDLING" -> ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype
    "VURDERT_TILBAKEKREVINGSBEHANDLING" -> VurdertTilbakekrevingsbehandlingHendelsestype
    "OPPDATERT_VEDTAKSBREV_TILBAKEKREVINGSBEHANDLING" -> OppdatertVedtaksbrevTilbakekrevingsbehandlingHendelsestype
    "TILBAKEKREVINGSBEHANDLING_TIL_ATTESTERING" -> TilbakekrevingsbehandlingTilAttesteringHendelsestype
    "UNDERKJENT_TILBAKEKREVINGSBEHANDLING" -> UnderkjentTilbakekrevingsbehandlingHendelsestype
    "IVERKSATT_TILBAKEKREVINGSBEHANDLING" -> IverksattTilbakekrevingsbehandlingHendelsestype
    "AVBRUTT_TILBAKEKREVINGSBEHANDLING" -> AvbruttTilbakekrevingsbehandlingHendelsestype
    "OPPDATERT_KRAVGRUNNLAG" -> OppdatertKravgrunnlagPåTilbakekrevingHendelse
    "NOTAT_TILBAKEKREVINGSBEHANDLING" -> NotatTilbakekrevingsbehandlingHendelsestype
    else -> throw IllegalStateException("Ukjent hendelsestype for tilbakekreving - fikk $this")
}

internal fun Hendelsestype.erÅpen(): Boolean = when (this) {
    OpprettetTilbakekrevingsbehandlingHendelsestype,
    ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype,
    VurdertTilbakekrevingsbehandlingHendelsestype,
    OppdatertVedtaksbrevTilbakekrevingsbehandlingHendelsestype,
    TilbakekrevingsbehandlingTilAttesteringHendelsestype,
    UnderkjentTilbakekrevingsbehandlingHendelsestype,
    OppdatertKravgrunnlagPåTilbakekrevingHendelse,
    NotatTilbakekrevingsbehandlingHendelsestype,
    -> true

    IverksattTilbakekrevingsbehandlingHendelsestype,
    AvbruttTilbakekrevingsbehandlingHendelsestype,
    -> false

    else -> throw IllegalStateException("Ukjent hendelsestype for tilbakekreving - fikk $this")
}

internal fun Hendelsestype.toBehandlingssamendragStatus(): Behandlingssammendrag.Behandlingsstatus = when (this) {
    OpprettetTilbakekrevingsbehandlingHendelsestype,
    ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype,
    VurdertTilbakekrevingsbehandlingHendelsestype,
    OppdatertVedtaksbrevTilbakekrevingsbehandlingHendelsestype,
    OppdatertKravgrunnlagPåTilbakekrevingHendelse,
    NotatTilbakekrevingsbehandlingHendelsestype,
    -> Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING

    TilbakekrevingsbehandlingTilAttesteringHendelsestype -> Behandlingssammendrag.Behandlingsstatus.TIL_ATTESTERING
    UnderkjentTilbakekrevingsbehandlingHendelsestype -> Behandlingssammendrag.Behandlingsstatus.UNDERKJENT
    IverksattTilbakekrevingsbehandlingHendelsestype -> Behandlingssammendrag.Behandlingsstatus.IVERKSATT
    AvbruttTilbakekrevingsbehandlingHendelsestype -> Behandlingssammendrag.Behandlingsstatus.AVSLAG
    else -> throw IllegalStateException("Ukjent hendelsestype for tilbakekreving - fikk $this")
}

fun List<Tuple5<Long, Hendelsestype, Saksnummer, TilbakekrevingsbehandlingId, Tidspunkt>>.toBehandlingssammendrag(): List<Behandlingssammendrag> =
    this.map {
        Behandlingssammendrag(
            saksnummer = it.third,
            periode = null,
            behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
            behandlingStartet = it.fifth,
            status = it.second.toBehandlingssamendragStatus(),
        )
    }
