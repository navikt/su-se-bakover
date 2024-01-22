package no.nav.su.se.bakover.test.persistence.tilbakekreving

import arrow.core.Tuple5
import arrow.core.Tuple8
import dokument.domain.DokumentHendelser
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.hendelse.defaultHendelseMetadata
import no.nav.su.se.bakover.test.nyAvbruttTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyForhåndsvarsletTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyIverksattTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyOppdaterVedtaksbrevTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyOpprettetTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyTilbakekrevingsbehandlingTilAttesteringHendelse
import no.nav.su.se.bakover.test.nyUnderkjentTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyVurdertTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import tilbakekreving.domain.AvbruttHendelse
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelser
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagDetaljerPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagPåSakHendelser
import tilbakekreving.domain.kravgrunnlag.rått.RåttKravgrunnlagHendelse
import tilbakekreving.infrastructure.repo.TilbakekrevingsbehandlingPostgresRepo
import tilbakekreving.infrastructure.repo.kravgrunnlag.KravgrunnlagPostgresRepo
import tilbakekreving.infrastructure.repo.sammendrag.BehandlingssammendragKravgrunnlagPostgresRepo
import tilbakekreving.infrastructure.repo.sammendrag.BehandlingssammendragTilbakekrevingPostgresRepo
import økonomi.domain.utbetaling.Utbetaling

class PersistertTilbakekrevingTestData(
    databaseRepos: DatabaseRepos,
    sessionFactory: SessionFactory,
    private val hendelseRepo: HendelseRepo,
    kravgrunnlagPostgresRepo: KravgrunnlagPostgresRepo,
    dokumentHendelseRepo: DokumentHendelseRepo,
    dbMetrics: DbMetrics,
    private val testDataHelper: TestDataHelper,
) {
    val tilbakekrevingRepo = databaseRepos.tilbakekrevingRepo
    val tilbakekrevingHendelseRepo = TilbakekrevingsbehandlingPostgresRepo(
        sessionFactory = sessionFactory,
        hendelseRepo = hendelseRepo,
        clock = fixedClock,
        kravgrunnlagRepo = kravgrunnlagPostgresRepo,
        dokumentHendelseRepo = dokumentHendelseRepo,
    )

    val behandlingssammendragKravgrunnlagPostgresRepo =
        BehandlingssammendragKravgrunnlagPostgresRepo(
            dbMetrics = dbMetrics,
            sessionFactory = sessionFactory,
        )

    val behandlingssammendragTilbakekrevingPostgresRepo = BehandlingssammendragTilbakekrevingPostgresRepo(
        dbMetrics = dbMetrics,
        sessionFactory = sessionFactory,
    )

    fun persisterUnderkjentTilbakekrevingsbehandlingHendelse(): Tuple5<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, TilbakekrevingsbehandlingHendelser> {
        return persisterVedtaksbrevTilbakekrevingsbehandlingHendelse().let {
            Tuple5(
                first = it.first,
                second = it.second,
                third = it.third,
                fourth = it.fourth,
                fifth = it.fifth.let { hendelser ->
                    nyUnderkjentTilbakekrevingsbehandlingHendelse(
                        forrigeHendelse = hendelser.last(),
                        versjon = hendelser.last().versjon.inc(),
                        kravgrunnlagPåSakHendelseId = hendelser.currentState.behandlinger.first().kravgrunnlag.hendelseId,
                    ).let {
                        tilbakekrevingHendelseRepo.lagre(it, defaultHendelseMetadata())
                        // Ingen asynke hendelser trigger av denne hendelsen.
                        hendelser.leggTil(it)
                    }
                },
            )
        }
    }

    fun persisterTilbakekrevingsbehandlingTilAttesteringHendelse(): Tuple5<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, TilbakekrevingsbehandlingHendelser> {
        return persisterVedtaksbrevTilbakekrevingsbehandlingHendelse().let {
            Tuple5(
                first = it.first,
                second = it.second,
                third = it.third,
                fourth = it.fourth,
                fifth = it.fifth.let { hendelser ->
                    nyTilbakekrevingsbehandlingTilAttesteringHendelse(
                        forrigeHendelse = hendelser.last(),
                        versjon = hendelser.last().versjon.inc(),
                        kravgrunnlagPåSakHendelseId = hendelser.currentState.behandlinger.first().kravgrunnlag.hendelseId,
                    ).let {
                        tilbakekrevingHendelseRepo.lagre(it, defaultHendelseMetadata())
                        // Ingen asynke hendelser trigger av denne hendelsen.
                        hendelser.leggTil(it)
                    }
                },
            )
        }
    }

    fun persisterVedtaksbrevTilbakekrevingsbehandlingHendelse(): Tuple5<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, TilbakekrevingsbehandlingHendelser> {
        return persisterVurdertTilbakekrevingsbehandlingHendelse().let {
            Tuple5(
                first = it.first,
                second = it.second,
                third = it.third,
                fourth = it.fourth,
                fifth = it.fifth.let { hendelser ->
                    nyOppdaterVedtaksbrevTilbakekrevingsbehandlingHendelse(
                        forrigeHendelse = hendelser.last(),
                        versjon = hendelser.last().versjon.inc(),
                        kravgrunnlagPåSakHendelseId = hendelser.currentState.behandlinger.first().kravgrunnlag.hendelseId,
                    ).let {
                        tilbakekrevingHendelseRepo.lagre(it, defaultHendelseMetadata())
                        // Ingen asynke hendelser trigger av denne hendelsen.
                        hendelser.leggTil(it)
                    }
                },
            )
        }
    }

    fun persisterVurdertTilbakekrevingsbehandlingHendelse(): Tuple5<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, TilbakekrevingsbehandlingHendelser> {
        return persisterForhåndsvarsletTilbakekrevingsbehandlingHendelse().let {
            Tuple5(
                first = it.first,
                second = it.second,
                third = it.third,
                fourth = it.fourth,
                fifth = it.fifth.let { hendelser ->
                    nyVurdertTilbakekrevingsbehandlingHendelse(
                        forrigeHendelse = hendelser.last(),
                        versjon = hendelser.last().versjon.inc(),
                        kravgrunnlagPåSakHendelseId = hendelser.currentState.behandlinger.first().kravgrunnlag.hendelseId,
                    ).let {
                        tilbakekrevingHendelseRepo.lagre(it, defaultHendelseMetadata())
                        // Ingen asynke hendelser trigger av denne hendelsen.
                        hendelser.leggTil(it)
                    }
                },
            )
        }
    }

    fun persisterForhåndsvarsletTilbakekrevingsbehandlingHendelse(): Tuple5<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, TilbakekrevingsbehandlingHendelser> {
        return persisterOpprettetTilbakekrevingsbehandlingHendelse().let {
            Tuple5(
                first = it.first,
                second = it.second,
                third = it.third,
                fourth = it.fourth,
                fifth = TilbakekrevingsbehandlingHendelser.create(
                    sakId = it.first.id,
                    hendelser = listOf(
                        it.seventh,
                        nyForhåndsvarsletTilbakekrevingsbehandlingHendelse(
                            forrigeHendelse = it.seventh,
                            // Oppgaven blir lagret etter.
                            versjon = it.eighth.versjon.inc(),
                            kravgrunnlagPåSakHendelseId = it.sixth.kravgrunnlag.hendelseId,
                        ).also {
                            tilbakekrevingHendelseRepo.lagre(it, defaultHendelseMetadata())
                            // TODO jah: Utføres en asynk oppgave+dokumenthendelse etter dette.
                        },
                    ),
                    clock = fixedClock,
                    kravgrunnlagPåSak = KravgrunnlagPåSakHendelser(listOf(it.sixth)),
                    dokumentHendelser = DokumentHendelser.empty(it.first.id),
                ),
            )
        }
    }

    /**
     * Oppretter en søknadsbehandling for 2021 og opphører samme perioden.
     * Setter skalUtsetteTilbakekreving til true (dvs.) vi ikke gjør noen tilbakekrevingsbehandling i revurderinga.
     * Må passe på og sette klokka fram i tid, hvis ikke vil ikke søknadsbehandlingene bli utbetalt.
     *
     */
    fun persisterOpprettetTilbakekrevingsbehandlingHendelse(): Tuple8<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, RåttKravgrunnlagHendelse, KravgrunnlagDetaljerPåSakHendelse, OpprettetTilbakekrevingsbehandlingHendelse, OppgaveHendelse> {
        return testDataHelper.persisterRevurderingIverksattOpphørt(
            skalUtsetteTilbakekreving = true,
        ).let { (sak, revurdering, utbetaling, vedtak, råttKravgrunnlagHendelse, kravgrunnlagPåSakHendelse) ->
            nyOpprettetTilbakekrevingsbehandlingHendelse(
                sakId = sak.id,
                kravgrunnlagPåSakHendelseId = sak.uteståendeKravgrunnlag!!.hendelseId,
                versjon = sak.versjon.inc(),
            ).let { opprettetHendelse ->
                tilbakekrevingHendelseRepo.lagre(opprettetHendelse, defaultHendelseMetadata())
                val oppgaveHendelse = testDataHelper.persisterOppgaveHendelseFraRelatertHendelse { opprettetHendelse }
                Tuple8(
                    first = sak,
                    second = revurdering,
                    third = utbetaling,
                    fourth = vedtak,
                    fifth = råttKravgrunnlagHendelse!!,
                    sixth = kravgrunnlagPåSakHendelse!!,
                    seventh = opprettetHendelse,
                    eighth = oppgaveHendelse,
                )
            }
        }
    }

    fun persisterAvbruttTilbakekrevingsbehandlingHendelse(
        forrigeHendelse: Tuple8<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, RåttKravgrunnlagHendelse, KravgrunnlagDetaljerPåSakHendelse, OpprettetTilbakekrevingsbehandlingHendelse, OppgaveHendelse> = persisterOpprettetTilbakekrevingsbehandlingHendelse(),
    ): Tuple8<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, RåttKravgrunnlagHendelse, KravgrunnlagDetaljerPåSakHendelse, AvbruttHendelse, OppgaveHendelse> {
        return forrigeHendelse.let { (sak, revurdering, utbetaling, vedtak, råttKravgrunnlagHendelse, kravgrunnlagPåSakHendelse, opprettetHendelse) ->
            nyAvbruttTilbakekrevingsbehandlingHendelse(
                forrigeHendelse = opprettetHendelse,
                versjon = hendelseRepo.hentSisteVersjonFraEntitetId(sak.id)!!.inc(),
                kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelse.hendelseId,
            ).let { avbruttHendelse ->
                tilbakekrevingHendelseRepo.lagre(avbruttHendelse, defaultHendelseMetadata())
                val oppgaveHendelse = testDataHelper.persisterOppgaveHendelseFraRelatertHendelse { avbruttHendelse }
                Tuple8(
                    first = sak,
                    second = revurdering,
                    third = utbetaling,
                    fourth = vedtak,
                    fifth = råttKravgrunnlagHendelse,
                    sixth = kravgrunnlagPåSakHendelse,
                    seventh = avbruttHendelse,
                    eighth = oppgaveHendelse,
                )
            }
        }
    }

    fun persisterIverksattTilbakekrevingsbehandlingHendelse(): Tuple5<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, TilbakekrevingsbehandlingHendelser> {
        return persisterTilbakekrevingsbehandlingTilAttesteringHendelse().let {
            Tuple5(
                first = it.first,
                second = it.second,
                third = it.third,
                fourth = it.fourth,
                fifth = it.fifth.let { hendelser ->
                    nyIverksattTilbakekrevingsbehandlingHendelse(
                        forrigeHendelse = hendelser.last(),
                        versjon = hendelser.last().versjon.inc(),
                        kravgrunnlagPåSakHendelseId = hendelser.currentState.behandlinger.first().kravgrunnlag.hendelseId,
                    ).let {
                        tilbakekrevingHendelseRepo.lagre(it, defaultHendelseMetadata())
                        // Ingen asynke hendelser trigger av denne hendelsen.
                        hendelser.leggTil(it)
                    }
                },
            )
        }
    }
}
