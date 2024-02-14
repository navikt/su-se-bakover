package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.extensions.toPeriode
import no.nav.su.se.bakover.common.tid.periode.Periode
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
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fnr
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
import person.domain.KunneIkkeHentePerson
import java.time.temporal.ChronoUnit
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
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
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
                    it.perioder == listOf(
                        søknadsbehandling.periode,
                    )
                }
                opprettetRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(
                    mapOf(
                        Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                    ),
                )

                inOrder(
                    *mock.all(),
                ) {
                    verify(mock.sakService).hentSak(sak.id)
                    verify(mock.personService).hentAktørId(argThat { it shouldBe fnr })
                    verify(mock.oppgaveService).opprettOppgave(
                        argThat {
                            it shouldBe OppgaveConfig.Revurderingsbehandling(
                                saksnummer = saksnummer,
                                aktørId = aktørId,
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

        val fraOgMedDatoFebruar = fixedLocalDate.plus(1, ChronoUnit.MONTHS)
        val fraOgMedDatoApril = fixedLocalDate.plus(3, ChronoUnit.MONTHS)

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak3.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            revurderingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            clock = clock,
        ).also {
            val revurderingForFebruar = it.revurderingService.opprettRevurdering(

                OpprettRevurderingCommand(
                    sakId = sakId,
                    periode = fraOgMedDatoFebruar.rangeTo(vedtaksperiode.tilOgMed).toPeriode(),
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
                    periode = fraOgMedDatoApril.rangeTo(vedtaksperiode.tilOgMed).toPeriode(),
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
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            revurderingRepo = mock(),
            clock = clock,
        ).also { mocks ->
            val actual = mocks.revurderingService.opprettRevurdering(
                OpprettRevurderingCommand(
                    sakId = sak.id,
                    periode = februar(2021).fraOgMed.rangeTo(revurderingVedtak.periode.tilOgMed)
                        .toPeriode(),
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
            verify(mocks.personService).hentAktørId(argThat { it shouldBe sak.fnr })
            verify(mocks.revurderingRepo).defaultTransactionContext()
            verify(mocks.revurderingRepo).lagre(argThat { it.right() shouldBe actual }, anyOrNull())
            verify(mocks.oppgaveService).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Revurderingsbehandling(
                        saksnummer = sak.saksnummer,
                        aktørId = aktørId,
                        tilordnetRessurs = saksbehandler,
                        clock = mocks.clock,
                    )
                },
            )
            mocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `fant ikke aktør id`() {
        val (sak, iverksatt) = iverksattSøknadsbehandlingUføre()
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
            },
        ).also {
            it.revurderingService.opprettRevurdering(
                OpprettRevurderingCommand(
                    sakId = sakId,
                    periode = februar(2021).fraOgMed.rangeTo(iverksatt.periode.tilOgMed)
                        .toPeriode(),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
                ),
            ) shouldBe KunneIkkeOppretteRevurdering.FantIkkeAktørId(KunneIkkeHentePerson.FantIkkePerson).left()

            verify(it.sakService).hentSak(sakId)
            verify(it.personService).hentAktørId(argThat { it shouldBe fnr })
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kunne ikke opprette oppgave`() {
        val (sak, iverksatt) = iverksattSøknadsbehandlingUføre()
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
        ).also { mocks ->
            val actual = mocks.revurderingService.opprettRevurdering(
                OpprettRevurderingCommand(
                    sakId = sakId,
                    periode = februar(2021).fraOgMed.rangeTo(iverksatt.periode.tilOgMed)
                        .toPeriode(),
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
            verify(mocks.personService).hentAktørId(argThat { it shouldBe fnr })
            verify(mocks.oppgaveService).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Revurderingsbehandling(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
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
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
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
                    personopplysninger = Personopplysninger(sakMedFørstegangsbehandling.first.fnr),
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
                    periode = 1.januar(2021).rangeTo(sakMedNyStønadsperiode.third.periode.tilOgMed).toPeriode(),
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
