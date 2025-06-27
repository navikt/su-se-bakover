package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.revurdering.opprett.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.domain.revurdering.opprett.OpprettRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.steg.Vurderingstatus
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.FantIkkeSak
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FnrWrapper
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.vedtakRevurdering
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import vedtak.domain.VedtakSomKanRevurderes
import java.util.UUID

internal class OpprettRevurderingServiceTest {
    @Test
    fun `oppretter en revurdering`() {
        val (sak, søknadsbehandling, søknadsvedtak) = iverksattSøknadsbehandlingUføre()
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
            revurderingRepo = mock(),
        ).also { mock ->
            val actual = mock.revurderingService.opprettRevurdering(
                OpprettRevurderingCommand(
                    sakId = sakId,
                    periode = søknadsbehandling.periode,
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(
                        Revurderingsteg.Inntekt,
                    ),
                ),
            ).getOrFail()

            actual.let { opprettetRevurdering ->
                opprettetRevurdering.periode shouldBe søknadsbehandling.periode
                opprettetRevurdering.tilRevurdering shouldBe søknadsvedtak.id
                opprettetRevurdering.saksbehandler shouldBe saksbehandler
                opprettetRevurdering.oppgaveId shouldBe OppgaveId("123")
                opprettetRevurdering.revurderingsårsak shouldBe Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MELDING_FRA_BRUKER.toString(),
                    begrunnelse = "Ny informasjon",
                )
                opprettetRevurdering.vilkårsvurderinger.erLik(søknadsbehandling.vilkårsvurderinger)
                opprettetRevurdering.vilkårsvurderinger.vilkår.all {
                    it.perioderSlåttSammen == listOf(
                        søknadsbehandling.periode,
                    )
                }
                opprettetRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.opprettMedVurderinger(
                    sak.type,
                    mapOf(
                        Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                    ),
                )

                inOrder(
                    *mock.all(),
                ) {
                    verify(mock.sakService).hentSak(sak.id)
                    verify(mock.oppgaveService).opprettOppgave(
                        argThat {
                            it shouldBe OppgaveConfig.Revurderingsbehandling(
                                saksnummer = saksnummer,
                                fnr = sak.fnr,
                                tilordnetRessurs = saksbehandler,
                                clock = mock.clock,
                            )
                        },
                    )
                    verify(mock.revurderingRepo).defaultTransactionContext()
                    verify(mock.revurderingRepo).lagre(argThat { it.right() shouldBe actual.right() }, anyOrNull())
                }

                mock.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `fant ikke sak`() {
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn FantIkkeSak.left()
            },
        ).also {
            shouldThrow<NullPointerException> {
                it.revurderingService.opprettRevurdering(
                    OpprettRevurderingCommand(
                        sakId = sakId,
                        periode = år(2021),
                        årsak = "MELDING_FRA_BRUKER",
                        begrunnelse = "Ny informasjon",
                        saksbehandler = saksbehandler,
                        informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
                    ),
                )
            }.message shouldBe null
        }
    }

    @Test
    fun `kan ikke opprette revurdering hvis ingen vedtak eksisterer for angitt fra og med dato`() {
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn nySakUføre().first.right()
            },
        ).also {
            it.revurderingService.opprettRevurdering(
                OpprettRevurderingCommand(
                    sakId = sakId,
                    periode = år(2021),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                ),
            ) shouldBe KunneIkkeOppretteRevurdering.VedtakInnenforValgtPeriodeKanIkkeRevurderes(
                Sak.GjeldendeVedtaksdataErUgyldigForRevurdering.FantIngenVedtakSomKanRevurderes,
            ).left()
        }
    }

    @Test
    fun `for en ny revurdering vil det tas utgangspunkt i nyeste vedtak hvor fraOgMed er inni perioden`() {
        val vedtaksperiode = år(2021)

        val clock = TikkendeKlokke(fixedClock)

        val (sak1, _, vedtakForFørsteJanuarLagetForLengeSiden) = iverksattSøknadsbehandlingUføre(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(vedtaksperiode),
        )
        val (sak2, vedtakForFørsteJanuarLagetNå) = vedtakRevurdering(
            clock = clock,
            revurderingsperiode = vedtaksperiode,
            sakOgVedtakSomKanRevurderes = sak1 to vedtakForFørsteJanuarLagetForLengeSiden as VedtakSomKanRevurderes,
        )
        val (sak3, vedtakForFørsteMarsLagetNå) = vedtakRevurdering(
            clock = clock,
            revurderingsperiode = Periode.create(1.mars(2021), 31.desember(2021)),
            sakOgVedtakSomKanRevurderes = sak2 to vedtakForFørsteJanuarLagetNå,
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak3.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
            revurderingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            clock = clock,
        ).also {
            val revurderingForFebruar = it.revurderingService.opprettRevurdering(

                OpprettRevurderingCommand(
                    sakId = sakId,
                    periode = februar(2021)..desember(2021),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt, Revurderingsteg.Bosituasjon),
                ),
            )

            revurderingForFebruar.getOrFail().tilRevurdering shouldBe vedtakForFørsteJanuarLagetNå.id

            val revurderingForApril = it.revurderingService.opprettRevurdering(
                OpprettRevurderingCommand(
                    sakId = sakId,
                    periode = april(2021)..desember(2021),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(
                        Revurderingsteg.Inntekt,
                    ),
                ),
            )

            revurderingForApril.getOrFail().tilRevurdering shouldBe vedtakForFørsteMarsLagetNå.id
        }
    }

    @Test
    fun `kan revurdere en periode med eksisterende revurdering`() {
        val clock = tikkendeFixedClock()
        val (sak, revurderingVedtak) = vedtakRevurdering(
            clock = clock,
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
            revurderingRepo = mock(),
            clock = clock,
        ).also { mocks ->
            val actual = mocks.revurderingService.opprettRevurdering(
                OpprettRevurderingCommand(
                    sakId = sak.id,
                    periode = februar(2021)..desember(2021),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(
                        Revurderingsteg.Inntekt,
                    ),
                ),
            )

            actual.getOrFail().also {
                it.saksnummer shouldBe sak.saksnummer
                it.tilRevurdering shouldBe revurderingVedtak.id
            }

            verify(mocks.sakService).hentSak(sak.id)
            verify(mocks.revurderingRepo).defaultTransactionContext()
            verify(mocks.revurderingRepo).lagre(argThat { it.right() shouldBe actual }, anyOrNull())
            verify(mocks.oppgaveService).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Revurderingsbehandling(
                        saksnummer = sak.saksnummer,
                        fnr = sak.fnr,
                        tilordnetRessurs = saksbehandler,
                        clock = mocks.clock,
                    )
                },
            )
            mocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kunne ikke opprette oppgave`() {
        val (sak, _) = iverksattSøknadsbehandlingUføre()
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
            },
        ).also { mocks ->
            val actual = mocks.revurderingService.opprettRevurdering(
                OpprettRevurderingCommand(
                    sakId = sakId,
                    periode = februar(2021)..desember(2021),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(
                        Revurderingsteg.Inntekt,
                    ),
                ),
            )
            actual shouldBe KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave(KunneIkkeOppretteOppgave).left()
            verify(mocks.sakService).hentSak(sakId)
            verify(mocks.oppgaveService).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Revurderingsbehandling(
                        saksnummer = saksnummer,
                        fnr = sak.fnr,
                        tilordnetRessurs = saksbehandler,
                        clock = mocks.clock,
                    )
                },
            )
            mocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `må velge noe som skal revurderes`() {
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn nySakUføre().first.right()
            },
        ).also {
            it.revurderingService.opprettRevurdering(
                OpprettRevurderingCommand(
                    sakId = sakId,
                    periode = år(2021),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = emptyList(),
                ),
            ) shouldBe KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes.left()
        }
    }

    @Test
    fun `støtter ikke tilfeller hvor gjeldende vedtaksdata ikke er sammenhengende i tid`() {
        val clock = TikkendeKlokke()
        val sakMedFørstegangsbehandling = iverksattSøknadsbehandlingUføre(
            stønadsperiode = Stønadsperiode.create(januar(2021).rangeTo(juli(2021))),
            clock = clock,
        )

        val sakMedNyStønadsperiode = iverksattSøknadsbehandlingUføre(
            stønadsperiode = Stønadsperiode.create(januar(2022).rangeTo(desember(2022))),
            sakOgSøknad = sakMedFørstegangsbehandling.first to nySøknadJournalførtMedOppgave(
                sakId = sakMedFørstegangsbehandling.first.id,
                søknadInnhold = søknadinnholdUføre(
                    personopplysninger = FnrWrapper(sakMedFørstegangsbehandling.first.fnr),
                ),
            ),
            clock = clock,
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sakMedNyStønadsperiode.first.right()
            },
        ).also {
            val actual = it.revurderingService.opprettRevurdering(
                OpprettRevurderingCommand(
                    sakId = sakId,
                    periode = januar(2021)..desember(2021),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(
                        Revurderingsteg.Inntekt,
                    ),
                ),
            )
            actual shouldBe KunneIkkeOppretteRevurdering.VedtakInnenforValgtPeriodeKanIkkeRevurderes(
                Sak.GjeldendeVedtaksdataErUgyldigForRevurdering.HeleRevurderingsperiodenInneholderIkkeVedtak,
            ).left()
        }
    }
}
