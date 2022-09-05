package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.common.toPeriode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.erLik
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.stønadsperiodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.søknadinnhold
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
                on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            revurderingRepo = mock(),
        ).also {
            val actual = it.revurderingService.opprettRevurdering(
                OpprettRevurderingRequest(
                    sakId = sakId,
                    periode = periodeNesteMånedOgTreMånederFram.fraOgMed.rangeTo(søknadsvedtak.periode.tilOgMed).toPeriode(),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(
                        Revurderingsteg.Inntekt,
                    ),
                ),
            ).getOrFail()

            actual.let { opprettetRevurdering ->
                opprettetRevurdering.periode shouldBe Periode.create(
                    periodeNesteMånedOgTreMånederFram.fraOgMed,
                    søknadsbehandling.periode.tilOgMed,
                )
                opprettetRevurdering.tilRevurdering shouldBe søknadsvedtak.id
                opprettetRevurdering.saksbehandler shouldBe saksbehandler
                opprettetRevurdering.oppgaveId shouldBe OppgaveId("oppgaveId")
                opprettetRevurdering.fritekstTilBrev shouldBe ""
                opprettetRevurdering.revurderingsårsak shouldBe Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MELDING_FRA_BRUKER.toString(), begrunnelse = "Ny informasjon",
                )
                opprettetRevurdering.forhåndsvarsel shouldBe null
                opprettetRevurdering.vilkårsvurderinger.erLik(søknadsbehandling.vilkårsvurderinger)
                opprettetRevurdering.vilkårsvurderinger.vilkår.all {
                    it.perioder == listOf(
                        periodeNesteMånedOgTreMånederFram,
                    )
                }
                opprettetRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(
                    mapOf(
                        Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                    ),
                )

                inOrder(
                    *it.all(),
                ) {
                    verify(it.sakService).hentSak(sak.id)
                    verify(it.personService).hentAktørId(argThat { it shouldBe fnr })
                    verify(it.oppgaveService).opprettOppgave(
                        argThat {
                            it shouldBe OppgaveConfig.Revurderingsbehandling(
                                saksnummer = saksnummer,
                                aktørId = aktørId,
                                tilordnetRessurs = null,
                                clock = fixedClock,
                            )
                        },
                    )
                    verify(it.revurderingRepo).defaultTransactionContext()
                    verify(it.revurderingRepo).lagre(argThat { it.right() shouldBe actual.right() }, anyOrNull())
                }

                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `kan opprette revurdering med årsak g-regulering i samme måned`() {
        val (sak, søknadsbehandling, søknadsvedtak) = iverksattSøknadsbehandlingUføre(
            stønadsperiode = stønadsperiodeNesteMånedOgTreMånederFram,
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            revurderingRepo = mock(),
        ).also {
            val actual = it.revurderingService.opprettRevurdering(
                OpprettRevurderingRequest(
                    sakId = sakId,
                    periode = periodeNesteMånedOgTreMånederFram.fraOgMed.rangeTo(søknadsvedtak.periode.tilOgMed).toPeriode(),
                    årsak = "REGULER_GRUNNBELØP",
                    begrunnelse = "g-regulering",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(
                        Revurderingsteg.Inntekt,
                    ),
                ),
            ).getOrHandle {
                throw RuntimeException("$it")
            }

            val periode =
                Periode.create(periodeNesteMånedOgTreMånederFram.fraOgMed, periodeNesteMånedOgTreMånederFram.tilOgMed)
            actual.let { opprettetRevurdering ->
                opprettetRevurdering.periode shouldBe periode
                opprettetRevurdering.tilRevurdering shouldBe søknadsvedtak.id
                opprettetRevurdering.saksbehandler shouldBe saksbehandler
                opprettetRevurdering.oppgaveId shouldBe OppgaveId("oppgaveId")
                opprettetRevurdering.fritekstTilBrev shouldBe ""
                opprettetRevurdering.revurderingsårsak shouldBe Revurderingsårsak(
                    Revurderingsårsak.Årsak.REGULER_GRUNNBELØP,
                    Revurderingsårsak.Begrunnelse.create("g-regulering"),
                )
                opprettetRevurdering.forhåndsvarsel shouldBe Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles
                opprettetRevurdering.vilkårsvurderinger.vilkår.erLik(søknadsbehandling.vilkårsvurderinger.vilkår)
                opprettetRevurdering.vilkårsvurderinger.vilkår.all { it.perioder == listOf(periode) }
                opprettetRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(
                    mapOf(
                        Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                    ),
                )
            }
            inOrder(
                *it.all(),
            ) {
                verify(it.sakService).hentSak(sakId)
                verify(it.personService).hentAktørId(argThat { it shouldBe fnr })
                verify(it.oppgaveService).opprettOppgave(
                    argThat {
                        it shouldBe OppgaveConfig.Revurderingsbehandling(
                            saksnummer = saksnummer,
                            aktørId = aktørId,
                            tilordnetRessurs = null,
                            clock = fixedClock,
                        )
                    },
                )
                verify(it.revurderingRepo).defaultTransactionContext()
                verify(it.revurderingRepo).lagre(argThat { it.right() shouldBe actual.right() }, anyOrNull())
            }

            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kan opprette revurdering med årsak g-regulering 1 kalendermåned tilbake i tid`() {
        val periode = Periode.create(
            periodeNesteMånedOgTreMånederFram.tilOgMed.minusMonths(1).startOfMonth(),
            periodeNesteMånedOgTreMånederFram.tilOgMed,
        )

        val (sak, _, søknadsvedtak) = iverksattSøknadsbehandlingUføre(
            stønadsperiode = Stønadsperiode.create(periode),
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
            },
            personService = mock<PersonService> {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            revurderingRepo = mock(),
        ).also {
            val actual = it.revurderingService.opprettRevurdering(
                OpprettRevurderingRequest(
                    sakId = sakId,
                    periode = periode.tilOgMed.minusMonths(1).startOfMonth().rangeTo(søknadsvedtak.periode.tilOgMed).toPeriode(),
                    årsak = "REGULER_GRUNNBELØP",
                    begrunnelse = "g-regulering",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(
                        Revurderingsteg.Inntekt,
                    ),
                ),
            ).getOrFail()

            actual.let { opprettetRevurdering ->
                opprettetRevurdering.periode shouldBe periode
                opprettetRevurdering.tilRevurdering shouldBe søknadsvedtak.id
                opprettetRevurdering.saksbehandler shouldBe saksbehandler
                opprettetRevurdering.oppgaveId shouldBe OppgaveId("oppgaveId")
                opprettetRevurdering.fritekstTilBrev shouldBe ""
                opprettetRevurdering.revurderingsårsak shouldBe Revurderingsårsak(
                    Revurderingsårsak.Årsak.REGULER_GRUNNBELØP,
                    Revurderingsårsak.Begrunnelse.create("g-regulering"),
                )
                opprettetRevurdering.forhåndsvarsel shouldBe Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles
                opprettetRevurdering.vilkårsvurderinger.vilkår.erLik(søknadsvedtak.behandling.vilkårsvurderinger.vilkår)
                opprettetRevurdering.vilkårsvurderinger.vilkår.all { it.perioder == listOf(periode) }
                opprettetRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(
                    mapOf(
                        Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                    ),
                )
            }

            inOrder(
                *it.all(),
            ) {
                verify(it.sakService).hentSak(sakId)
                verify(it.personService).hentAktørId(argThat { it shouldBe fnr })
                verify(it.oppgaveService).opprettOppgave(
                    argThat {
                        it shouldBe OppgaveConfig.Revurderingsbehandling(
                            saksnummer = saksnummer,
                            aktørId = aktørId,
                            tilordnetRessurs = null,
                            clock = fixedClock,
                        )
                    },
                )
                verify(it.revurderingRepo).defaultTransactionContext()
                verify(it.revurderingRepo).lagre(argThat { it.right() shouldBe actual.right() }, anyOrNull())
            }

            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `fant ikke sak`() {
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn FantIkkeSak.left()
            },
        ).also {
            it.revurderingService.opprettRevurdering(
                OpprettRevurderingRequest(
                    sakId = sakId,
                    periode = år(2021),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
                ),
            ) shouldBe KunneIkkeOppretteRevurdering.FantIkkeSak.left()
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
                OpprettRevurderingRequest(
                    sakId = sakId,
                    periode = år(2021),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                ),
            ) shouldBe KunneIkkeOppretteRevurdering.FeilVedOpprettelseAvRevurdering(
                Sak.KunneIkkeOppretteRevurdering.KunneIkkeHenteGjeldendeVedtaksdataSak(
                    Sak.KunneIkkeHenteGjeldendeVedtaksdata.FinnesIngenVedtakSomKanRevurderes(år(2021)),
                ),
            ).left()
        }
    }

    @Test
    fun `for en ny revurdering vil det tas utgangspunkt i nyeste vedtak hvor fraOgMed er inni perioden`() {
        val vedtaksperiode = år(2021)

        val tikkendeKlokke = TikkendeKlokke(fixedClock)

        val (sak1, _, vedtakForFørsteJanuarLagetForLengeSiden) = iverksattSøknadsbehandlingUføre(
            stønadsperiode = Stønadsperiode.create(vedtaksperiode),
            clock = tikkendeKlokke,
        )
        val (sak2, vedtakForFørsteJanuarLagetNå) = vedtakRevurdering(
            sakOgVedtakSomKanRevurderes = sak1 to vedtakForFørsteJanuarLagetForLengeSiden as VedtakSomKanRevurderes,
            revurderingsperiode = vedtaksperiode,
            clock = tikkendeKlokke,
        )
        val (sak3, vedtakForFørsteMarsLagetNå) = vedtakRevurdering(
            sakOgVedtakSomKanRevurderes = sak2 to vedtakForFørsteJanuarLagetNå,
            revurderingsperiode = Periode.create(1.mars(2021), 31.desember(2021)),
            clock = tikkendeKlokke,
        )

        val fraOgMedDatoFebruar = fixedLocalDate.plus(1, ChronoUnit.MONTHS)
        val fraOgMedDatoApril = fixedLocalDate.plus(3, ChronoUnit.MONTHS)

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak3.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
            revurderingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
        ).also {
            val revurderingForFebruar = it.revurderingService.opprettRevurdering(
                OpprettRevurderingRequest(
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
                OpprettRevurderingRequest(
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
        val (sak, revurderingVedtak) = vedtakRevurdering()

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            revurderingRepo = mock(),
        ).also {
            val actual = it.revurderingService.opprettRevurdering(
                OpprettRevurderingRequest(
                    sakId = sakId,
                    periode = periodeNesteMånedOgTreMånederFram.fraOgMed.rangeTo(revurderingVedtak.periode.tilOgMed).toPeriode(),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(
                        Revurderingsteg.Inntekt,
                    ),
                ),
            )

            actual.getOrFail().also {
                it.saksnummer shouldBe saksnummer
                it.tilRevurdering shouldBe revurderingVedtak.id
            }

            verify(it.sakService).hentSak(sakId)
            verify(it.personService).hentAktørId(argThat { it shouldBe fnr })
            verify(it.revurderingRepo).defaultTransactionContext()
            verify(it.revurderingRepo).lagre(argThat { it.right() shouldBe actual }, anyOrNull())
            verify(it.oppgaveService).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Revurderingsbehandling(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
                        tilordnetRessurs = null,
                        clock = fixedClock,
                    )
                },
            )
            it.verifyNoMoreInteractions()
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
                OpprettRevurderingRequest(
                    sakId = sakId,
                    periode = periodeNesteMånedOgTreMånederFram.fraOgMed.rangeTo(iverksatt.periode.tilOgMed).toPeriode(),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
                ),
            ) shouldBe KunneIkkeOppretteRevurdering.FeilVedOpprettelseAvRevurdering(Sak.KunneIkkeOppretteRevurdering.FantIkkeAktørId)
                .left()

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
        ).also {
            val actual = it.revurderingService.opprettRevurdering(
                OpprettRevurderingRequest(
                    sakId = sakId,
                    periode = periodeNesteMånedOgTreMånederFram.fraOgMed.rangeTo(iverksatt.periode.tilOgMed).toPeriode(),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(
                        Revurderingsteg.Inntekt,
                    ),
                ),
            )
            actual shouldBe KunneIkkeOppretteRevurdering.FeilVedOpprettelseAvRevurdering(Sak.KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave)
                .left()
            verify(it.sakService).hentSak(sakId)
            verify(it.personService).hentAktørId(argThat { it shouldBe fnr })
            verify(it.oppgaveService).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Revurderingsbehandling(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
                        tilordnetRessurs = null,
                        clock = fixedClock,
                    )
                },
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `må velge noe som skal revurderes`() {
        RevurderingServiceMocks(
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
        ).also {
            it.revurderingService.opprettRevurdering(
                OpprettRevurderingRequest(
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
        val sakMedFørstegangsbehandling = iverksattSøknadsbehandlingUføre(
            stønadsperiode = Stønadsperiode.create(januar(2021).rangeTo(juli(2021))),
        )

        val sakMedNyStønadsperiode = iverksattSøknadsbehandlingUføre(
            stønadsperiode = Stønadsperiode.create(januar(2022).rangeTo(desember(2022))),
            sakOgSøknad = sakMedFørstegangsbehandling.first to nySøknadJournalførtMedOppgave(
                sakId = sakMedFørstegangsbehandling.first.id,
                søknadInnhold = søknadinnhold(
                    fnr = sakMedFørstegangsbehandling.first.fnr,
                ),
            ),
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sakMedNyStønadsperiode.first.right()
            },
        ).also {
            val actual = it.revurderingService.opprettRevurdering(
                OpprettRevurderingRequest(
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
            actual shouldBe KunneIkkeOppretteRevurdering.FeilVedOpprettelseAvRevurdering(Sak.KunneIkkeOppretteRevurdering.TidslinjeForVedtakErIkkeKontinuerlig)
                .left()
        }
    }

    @Test
    fun `får feilmelding dersom saken har utestående avkorting, men revurderingsperioden inneholder ikke perioden for avkortingen`() {
        val clock = TikkendeKlokke(fixedClock)
        val (sak, _) = vedtakRevurdering(
            clock = clock,
            revurderingsperiode = Periode.create(1.juni(2021), 31.desember(2021)),
            vilkårOverrides = listOf(
                utenlandsoppholdAvslag(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = Periode.create(1.juni(2021), 31.desember(2021)),
                ),
            ),
        )
        val nyRevurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021))

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
        ).let {
            it.revurderingService.opprettRevurdering(
                OpprettRevurderingRequest(
                    sakId = sakId,
                    periode = nyRevurderingsperiode,
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Utenlandsopphold),
                ),
            ) shouldBe KunneIkkeOppretteRevurdering.FeilVedOpprettelseAvRevurdering(
                Sak.KunneIkkeOppretteRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(
                    juni(2021),
                ),
            )
                .left()
        }
    }
}
