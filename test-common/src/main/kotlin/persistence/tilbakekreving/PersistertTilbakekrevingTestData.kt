package no.nav.su.se.bakover.test.persistence.tilbakekreving

import arrow.core.Tuple5
import arrow.core.Tuple8
import dokument.domain.DokumentHendelser
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.test.hendelse.defaultHendelseMetadata
import no.nav.su.se.bakover.test.nyAvbruttTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyForhåndsvarsletTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyIverksattTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyOppdaterVedtaksbrevTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyOppdatertKravgrunnlagTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyOppdatertNotatTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyOpprettetTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyTilbakekrevingsbehandlingTilAttesteringHendelse
import no.nav.su.se.bakover.test.nyUnderkjentTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyVurdertTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import tilbakekreving.domain.AvbruttHendelse
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelser
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagDetaljerPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagPåSakHendelser
import tilbakekreving.domain.kravgrunnlag.rått.RåttKravgrunnlagHendelse
import tilbakekreving.infrastructure.repo.TilbakekrevingsbehandlingPostgresRepo
import tilbakekreving.infrastructure.repo.kravgrunnlag.KravgrunnlagPostgresRepo
import tilbakekreving.infrastructure.repo.sammendrag.BehandlingssammendragKravgrunnlagOgTilbakekrevingPostgresRepo
import vilkår.domain.Vilkår
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
    val clock = testDataHelper.clock
    val tilbakekrevingRepo = databaseRepos.tilbakekrevingRepo
    val tilbakekrevingHendelseRepo = TilbakekrevingsbehandlingPostgresRepo(
        sessionFactory = sessionFactory,
        hendelseRepo = hendelseRepo,
        clock = clock,
        kravgrunnlagRepo = kravgrunnlagPostgresRepo,
        dokumentHendelseRepo = dokumentHendelseRepo,
    )

    val behandlingssammendragTilbakekrevingPostgresRepo = BehandlingssammendragKravgrunnlagOgTilbakekrevingPostgresRepo(
        dbMetrics = dbMetrics,
        sessionFactory = sessionFactory,
    )

    /**
     * Oppretter en søknadsbehandling for 2021 og revurderer med 1000 kroner uføregrunnlag for hele perioden.
     * Setter skalUtsetteTilbakekreving til true (dvs.) vi ikke gjør noen tilbakekrevingsbehandling i revurderinga.
     * Setter klokka fram i tid, hvis ikke vil ikke søknadsbehandlingene bli utbetalt.
     */
    fun persisterOpprettetTilbakekrevingsbehandlingHendelse(
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        revurderingsperiode: Periode = stønadsperiode.periode,
        vilkårOverrides: List<Vilkår> = listOf(
            innvilgetUførevilkår(
                periode = revurderingsperiode,
                forventetInntekt = 1000,
                opprettet = Tidspunkt.now(clock),
            ),
        ),
    ): Tuple8<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, RåttKravgrunnlagHendelse, KravgrunnlagDetaljerPåSakHendelse, OpprettetTilbakekrevingsbehandlingHendelse, OppgaveHendelse> {
        return testDataHelper.persisterIverksattRevurdering(
            skalUtsetteTilbakekreving = true,
            stønadsperiode = stønadsperiode,
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = vilkårOverrides,
        ).let { (sak, revurdering, utbetaling, vedtak, råttKravgrunnlagHendelse, kravgrunnlagPåSakHendelse) ->
            nyOpprettetTilbakekrevingsbehandlingHendelse(
                sakId = sak.id,
                kravgrunnlagPåSakHendelseId = sak.uteståendeKravgrunnlag!!.hendelseId,
                versjon = sak.versjon.inc(),
                hendelsesTidspunkt = Tidspunkt.now(clock),
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

    fun persisterForhåndsvarsletTilbakekrevingsbehandlingHendelse(
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        revurderingsperiode: Periode = stønadsperiode.periode,
        vilkårOverrides: List<Vilkår> = listOf(
            innvilgetUførevilkår(
                periode = revurderingsperiode,
                forventetInntekt = 1000,
                opprettet = Tidspunkt.now(clock),
            ),
        ),
    ): Tuple5<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, TilbakekrevingsbehandlingHendelser> {
        return persisterOpprettetTilbakekrevingsbehandlingHendelse(
            stønadsperiode = stønadsperiode,
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = vilkårOverrides,
        ).let {
            Tuple5(
                first = it.first,
                second = it.second,
                third = it.third,
                fourth = it.fourth,
                fifth = TilbakekrevingsbehandlingHendelser.create(
                    sakId = it.first.id,
                    clock = clock,
                    hendelser = listOf(
                        it.seventh,
                        nyForhåndsvarsletTilbakekrevingsbehandlingHendelse(
                            forrigeHendelse = it.seventh,
                            // Oppgaven blir lagret etter.
                            versjon = it.eighth.versjon.inc(),
                            kravgrunnlagPåSakHendelseId = it.sixth.kravgrunnlag.hendelseId,
                            hendelsesTidspunkt = Tidspunkt.now(clock),
                        ).also {
                            tilbakekrevingHendelseRepo.lagre(it, defaultHendelseMetadata())
                            // TODO jah: Utføres en asynk oppgave+dokumenthendelse etter dette.
                        },
                    ),
                    kravgrunnlagPåSak = KravgrunnlagPåSakHendelser(listOf(it.sixth)),
                    dokumentHendelser = DokumentHendelser.empty(it.first.id),
                ),
            )
        }
    }

    fun persisterVurdertTilbakekrevingsbehandlingHendelse(
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        revurderingsperiode: Periode = stønadsperiode.periode,
        vilkårOverrides: List<Vilkår> = listOf(
            innvilgetUførevilkår(
                periode = revurderingsperiode,
                forventetInntekt = 1000,
                opprettet = Tidspunkt.now(clock),
            ),
        ),
    ): Tuple5<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, TilbakekrevingsbehandlingHendelser> {
        return persisterForhåndsvarsletTilbakekrevingsbehandlingHendelse(
            stønadsperiode = stønadsperiode,
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = vilkårOverrides,
        ).let {
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

                        hendelsesTidspunkt = Tidspunkt.now(clock),
                    ).let {
                        tilbakekrevingHendelseRepo.lagre(it, defaultHendelseMetadata())
                        // Ingen asynke hendelser trigger av denne hendelsen.
                        hendelser.leggTil(it)
                    }
                },
            )
        }
    }

    fun persisterVedtaksbrevTilbakekrevingsbehandlingHendelse(
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        revurderingsperiode: Periode = stønadsperiode.periode,
        vilkårOverrides: List<Vilkår> = listOf(
            innvilgetUførevilkår(
                periode = revurderingsperiode,
                forventetInntekt = 1000,
                opprettet = Tidspunkt.now(clock),
            ),
        ),
    ): Tuple5<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, TilbakekrevingsbehandlingHendelser> {
        return persisterVurdertTilbakekrevingsbehandlingHendelse(
            stønadsperiode = stønadsperiode,
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = vilkårOverrides,
        ).let {
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
                        hendelsesTidspunkt = Tidspunkt.now(clock),
                    ).let {
                        tilbakekrevingHendelseRepo.lagre(it, defaultHendelseMetadata())
                        // Ingen asynke hendelser trigger av denne hendelsen.
                        hendelser.leggTil(it)
                    }
                },
            )
        }
    }

    /**
     * Saksbehandler kan skrive et notat før/etter fritekst til vedtaksbrev. Ofte samtidig.
     */
    fun persisterNotatTilbakekrevingsbehandlingHendelse(
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        revurderingsperiode: Periode = stønadsperiode.periode,
        vilkårOverrides: List<Vilkår> = listOf(
            innvilgetUførevilkår(
                periode = revurderingsperiode,
                forventetInntekt = 1000,
                opprettet = Tidspunkt.now(clock),
            ),
        ),
    ): Tuple5<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, TilbakekrevingsbehandlingHendelser> {
        return persisterVedtaksbrevTilbakekrevingsbehandlingHendelse(
            stønadsperiode = stønadsperiode,
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = vilkårOverrides,
        ).let {
            Tuple5(
                first = it.first,
                second = it.second,
                third = it.third,
                fourth = it.fourth,
                fifth = it.fifth.let { hendelser ->
                    nyOppdatertNotatTilbakekrevingsbehandlingHendelse(
                        forrigeHendelse = hendelser.last(),
                        versjon = hendelser.last().versjon.inc(),
                        kravgrunnlagPåSakHendelseId = hendelser.currentState.behandlinger.first().kravgrunnlag.hendelseId,
                        hendelsesTidspunkt = Tidspunkt.now(clock),
                    ).let {
                        tilbakekrevingHendelseRepo.lagre(it, defaultHendelseMetadata())
                        // Ingen asynke hendelser trigger av denne hendelsen.
                        hendelser.leggTil(it)
                    }
                },
            )
        }
    }

    fun persisterOppdatertKravgrunnlagPåTilbakekrevingsbehandlingHendelse(
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        revurderingsperiode: Periode = stønadsperiode.periode,
        vilkårOverrides: List<Vilkår> = listOf(
            innvilgetUførevilkår(
                periode = revurderingsperiode,
                forventetInntekt = 1000,
                opprettet = Tidspunkt.now(clock),
            ),
        ),
    ): Tuple5<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, TilbakekrevingsbehandlingHendelser> {
        return persisterVedtaksbrevTilbakekrevingsbehandlingHendelse(
            stønadsperiode = stønadsperiode,
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = vilkårOverrides,
        ).let {
            val sak = it.first
            Tuple5(
                first = sak,
                second = it.second,
                third = it.third,
                fourth = it.fourth,
                fifth = it.fifth.let { hendelser ->
                    val (_, _, _, _, _, oppdatertKravgrunnlag: KravgrunnlagDetaljerPåSakHendelse?) = testDataHelper.persisterRevurderingIverksattOpphørt(
                        skalUtsetteTilbakekreving = true,
                        sakOgVedtak = sak to it.fourth,
                        nesteKravgrunnlagVersjon = hendelser.last().versjon.inc(),
                        periode = revurderingsperiode,
                    )

                    nyOppdatertKravgrunnlagTilbakekrevingsbehandlingHendelse(
                        forrigeHendelse = hendelser.last(),
                        versjon = hendelser.last().versjon.inc().inc(),
                        førsteKravgrunnlagPåSakHendelseId = hendelser.currentState.behandlinger.first().kravgrunnlag.hendelseId,
                        oppdatertKravgrunnlagPåSakHendelseId = oppdatertKravgrunnlag!!.hendelseId,
                        hendelsesTidspunkt = Tidspunkt.now(clock),
                    ).let {
                        tilbakekrevingHendelseRepo.lagre(it, defaultHendelseMetadata())
                        // Ingen asynke hendelser trigger av denne hendelsen.
                        hendelser.leggTil(it)
                    }
                },
            )
        }
    }

    fun persisterAvbruttTilbakekrevingsbehandlingHendelse(
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        revurderingsperiode: Periode = stønadsperiode.periode,
        vilkårOverrides: List<Vilkår> = listOf(
            innvilgetUførevilkår(
                periode = revurderingsperiode,
                forventetInntekt = 1000,
                opprettet = Tidspunkt.now(clock),
            ),
        ),
        forrigeHendelse: Tuple8<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, RåttKravgrunnlagHendelse, KravgrunnlagDetaljerPåSakHendelse, OpprettetTilbakekrevingsbehandlingHendelse, OppgaveHendelse> = persisterOpprettetTilbakekrevingsbehandlingHendelse(
            stønadsperiode = stønadsperiode,
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = vilkårOverrides,
        ),
    ): Tuple8<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, RåttKravgrunnlagHendelse, KravgrunnlagDetaljerPåSakHendelse, AvbruttHendelse, OppgaveHendelse> {
        return forrigeHendelse.let { (sak, revurdering, utbetaling, vedtak, råttKravgrunnlagHendelse, kravgrunnlagPåSakHendelse, opprettetHendelse) ->
            nyAvbruttTilbakekrevingsbehandlingHendelse(
                forrigeHendelse = opprettetHendelse,
                versjon = hendelseRepo.hentSisteVersjonFraEntitetId(sak.id)!!.inc(),
                kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelse.hendelseId,

                hendelsesTidspunkt = Tidspunkt.now(clock),
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

    fun persisterTilbakekrevingsbehandlingTilAttesteringHendelse(
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        revurderingsperiode: Periode = stønadsperiode.periode,
        vilkårOverrides: List<Vilkår> = listOf(
            innvilgetUførevilkår(
                periode = revurderingsperiode,
                forventetInntekt = 1000,
                opprettet = Tidspunkt.now(clock),
            ),
        ),
    ): Tuple5<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, TilbakekrevingsbehandlingHendelser> {
        return persisterVedtaksbrevTilbakekrevingsbehandlingHendelse(
            stønadsperiode = stønadsperiode,
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = vilkårOverrides,
        ).let {
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
                        hendelsesTidspunkt = Tidspunkt.now(clock),
                    ).let {
                        tilbakekrevingHendelseRepo.lagre(it, defaultHendelseMetadata())
                        // Ingen asynke hendelser trigger av denne hendelsen.
                        hendelser.leggTil(it)
                    }
                },
            )
        }
    }

    fun persisterUnderkjentTilbakekrevingsbehandlingHendelse(
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        revurderingsperiode: Periode = stønadsperiode.periode,
        vilkårOverrides: List<Vilkår> = listOf(
            innvilgetUførevilkår(
                periode = revurderingsperiode,
                forventetInntekt = 1000,
                opprettet = Tidspunkt.now(clock),
            ),
        ),
    ): Tuple5<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, TilbakekrevingsbehandlingHendelser> {
        return persisterVedtaksbrevTilbakekrevingsbehandlingHendelse(
            stønadsperiode = stønadsperiode,
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = vilkårOverrides,
        ).let {
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

                        hendelsesTidspunkt = Tidspunkt.now(clock),
                    ).let {
                        tilbakekrevingHendelseRepo.lagre(it, defaultHendelseMetadata())
                        // Ingen asynke hendelser trigger av denne hendelsen.
                        hendelser.leggTil(it)
                    }
                },
            )
        }
    }

    fun persisterIverksattTilbakekrevingsbehandlingHendelse(
        stønadsperiode: Stønadsperiode = stønadsperiode2021,
        revurderingsperiode: Periode = stønadsperiode.periode,
        vilkårOverrides: List<Vilkår> = listOf(
            innvilgetUførevilkår(
                periode = revurderingsperiode,
                forventetInntekt = 1000,
                opprettet = Tidspunkt.now(clock),
            ),
        ),
    ): Tuple5<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling.MedKvittering, VedtakEndringIYtelse, TilbakekrevingsbehandlingHendelser> {
        return persisterTilbakekrevingsbehandlingTilAttesteringHendelse(
            stønadsperiode = stønadsperiode,
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = vilkårOverrides,
        ).let {
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

                        hendelsesTidspunkt = Tidspunkt.now(clock),
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
