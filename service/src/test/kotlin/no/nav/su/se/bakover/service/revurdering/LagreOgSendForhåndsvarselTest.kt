package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.beregning.Tilbakekreving
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeTilbakekrev
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksbehandlerNavn
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.LocalDate
import java.util.UUID

internal class LagreOgSendForhåndsvarselTest {

    @Test
    fun `forhåndsvarsler en simulert-revurdering`() {
        val (sak, simulertRevurdering) = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
        )

        val mocks = RevurderingServiceMocks(
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn saksbehandlerNavn.right()
            },
            oppgaveService = mock {
                on { oppdaterOppgave(any(), any()) } doReturn Unit.right()
            },
            brevService = mock {
                on { lagBrev(any()) } doReturn "pdf".toByteArray().right()
            },
            personService = mock {
                on { hentPerson(any()) } doReturn person(fnr, aktørId).right()
            },
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        )

        val revurdering = mocks.revurderingService.lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        ).getOrFail()

        revurdering.forhåndsvarsel shouldBe Forhåndsvarsel.UnderBehandling.Sendt

        verify(mocks.revurderingRepo).hent(argThat { it shouldBe revurderingId })
        verify(mocks.personService).hentPerson(argThat { it shouldBe fnr })
        verify(mocks.brevService).lagBrev(
            argThat {
                it shouldBe LagBrevRequest.Forhåndsvarsel(
                    person = person(fnr, aktørId),
                    saksbehandlerNavn = saksbehandlerNavn,
                    fritekst = "",
                    dagensDato = fixedLocalDate,
                    saksnummer = simulertRevurdering.saksnummer,
                )
            },
        )
        verify(mocks.revurderingRepo).lagre(
            argThat {
                it shouldBe simulertRevurdering.copy(
                    forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt,
                )
            },
            anyOrNull(),
        )
        verify(mocks.brevService).lagreDokument(
            argThat {
                it should beOfType<Dokument.MedMetadata.Informasjon.Viktig>()
                it.generertDokument shouldBe "pdf".toByteArray()
                it.metadata shouldBe Dokument.Metadata(
                    sakId = revurdering.sakId,
                    revurderingId = revurdering.id,
                    bestillBrev = true,
                )
            },
            anyOrNull(),
        )
        verify(mocks.identClient).hentNavnForNavIdent(
            argThat { it shouldBe simulertRevurdering.saksbehandler },
        )
        verify(mocks.oppgaveService).oppdaterOppgave(
            oppgaveId = argThat { it shouldBe oppgaveIdRevurdering },
            beskrivelse = argThat { it shouldBe "Forhåndsvarsel er sendt." },
        )
        verify(mocks.sakService).hentSakForRevurdering(simulertRevurdering.id)
        verify(mocks.tilbakekrevingService).hentAvventerKravgrunnlag(any<UUID>())
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `forhåndsvarsler ikke en allerede forhåndsvarslet revurdering`() {
        val (sak, simulertRevurdering) = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt,
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            personService = mock {
                on { hentPerson(any()) } doReturn person(fnr, aktørId).right()
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).also {
            it.revurderingService.lagreOgSendForhåndsvarsel(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
                fritekst = "",
            ) shouldBe KunneIkkeForhåndsvarsle.UgyldigTilstandsovergangForForhåndsvarsling.left()
        }
    }

    @Test
    fun `kan endre fra ingen forhåndsvarsel til forhåndsvarsel`() {
        val (sak, simulertRevurdering) = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
        )

        val mocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            personService = mock {
                on { hentPerson(any()) } doReturn person(fnr, aktørId).right()
            },
            brevService = mock {
                on { lagBrev(any()) } doReturn "pdf".toByteArray().right()
            },
            oppgaveService = mock {
                on { oppdaterOppgave(any(), any()) } doReturn Unit.right()
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        )
        val response = mocks.revurderingService.lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        )

        response shouldBe simulertRevurdering.copy(
            forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt,
        ).right()

        verify(mocks.revurderingRepo).hent(simulertRevurdering.id)
        verify(mocks.personService).hentPerson(any())
        verify(mocks.brevService).lagBrev(any())
        verify(mocks.revurderingRepo).lagre(any(), anyOrNull())
        verify(mocks.brevService).lagreDokument(any(), anyOrNull())
        verify(mocks.oppgaveService).oppdaterOppgave(any(), any())
        verify(mocks.identClient).hentNavnForNavIdent(any())
        verify(mocks.sakService).hentSakForRevurdering(any())
        verify(mocks.tilbakekrevingService).hentAvventerKravgrunnlag(any<UUID>())
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `får ikke sendt forhåndsvarsel dersom det ikke er mulig å sende behandlingen videre til attestering`() {
        val (sak, simulertRevurdering) = simulertRevurdering(
            stønadsperiode = stønadsperiode2021,
            revurderingsperiode = mai(2021)..desember(2021),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt1000(
                    periode = mai(2021)..(desember(2021)),
                ),
            ),
            utbetalingerKjørtTilOgMed = 1.august(2021),
        ).also {
            it.second.shouldBeType<SimulertRevurdering.Innvilget>().also {
                it.harSimuleringFeilutbetaling() shouldBe true
            }
        }

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn listOf(
                    IkkeTilbakekrev(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        sakId = sakId,
                        revurderingId = revurderingId,
                        periode = mars(2021),
                    ).fullførBehandling(),
                )
            },
            toggleService = mock {
                on { isEnabled(any()) } doReturn false
            },
        ).also {
            it.revurderingService.lagreOgSendForhåndsvarsel(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
                fritekst = "",
            ) shouldBe KunneIkkeForhåndsvarsle.Attestering(
                KunneIkkeSendeRevurderingTilAttestering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving(
                    revurderingId = revurderingId,
                ),
            ).left()
        }
    }

    private fun testForhåndsvarslerIkkeGittRevurdering(revurdering: Revurdering) {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurdering
            },
        ).also {
            it.revurderingService.lagreOgSendForhåndsvarsel(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
                fritekst = "",
            ) shouldBe KunneIkkeForhåndsvarsle.MåVæreITilstandenSimulert(
                revurdering::class,
            ).left()
        }
    }

    @Test
    fun `forhåndsvarsler bare simulerte revurderinger`() {
        testForhåndsvarslerIkkeGittRevurdering(opprettetRevurdering().second)
        testForhåndsvarslerIkkeGittRevurdering(beregnetRevurdering().second)
        testForhåndsvarslerIkkeGittRevurdering(iverksattRevurdering().second)
    }

    @Test
    fun `forhåndsvarsel - hent person feilet`() {
        val (sak, simulertRevurdering) = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            forhåndsvarsel = null,
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
                on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).also {
            it.revurderingService.lagreOgSendForhåndsvarsel(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
                fritekst = "",
            ) shouldBe KunneIkkeForhåndsvarsle.FantIkkePerson.left()
        }
    }

    @Test
    fun `forhåndsvarsel - generering av dokument feiler`() {
        val (sak, simulertRevurdering) = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            forhåndsvarsel = null,
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
        )
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
                on { hentPerson(any()) } doReturn person(fnr, aktørId).right()
            },
            brevService = mock {
                on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).also {
            it.revurderingService.lagreOgSendForhåndsvarsel(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
                fritekst = "",
            ) shouldBe KunneIkkeForhåndsvarsle.KunneIkkeGenerereDokument.left()

            verify(it.revurderingRepo, never()).lagre(any(), anyOrNull())
            verify(it.brevService, never()).lagreDokument(any())
        }
    }

    @Test
    fun `forhåndsvarsel - oppdatering av oppgave feiler`() {
        val (sak, simulertRevurdering) = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
        )
        val mocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
                on { hentPerson(any()) } doReturn person(fnr, aktørId).right()
            },
            brevService = mock {
                on { lagBrev(any()) } doReturn "pdf".toByteArray().right()
            },
            oppgaveService = mock {
                on { oppdaterOppgave(any(), any()) } doReturn OppgaveFeil.KunneIkkeOppdatereOppgave.left()
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        )
        mocks.revurderingService.lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.KunneIkkeOppdatereOppgave.left()

        verify(mocks.revurderingRepo).hent(argThat { it shouldBe simulertRevurdering.id })
        verify(mocks.brevService).lagBrev(any())
        verify(mocks.revurderingRepo).lagre(
            argThat<SimulertRevurdering> {
                it shouldBe simulertRevurdering.copy(
                    forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt,
                )
            },
            anyOrNull(),
        )
        verify(mocks.brevService).lagreDokument(
            argThat {
                it should beOfType<Dokument.MedMetadata.Informasjon.Viktig>()
                it.generertDokument shouldBe "pdf".toByteArray()
                it.metadata shouldBe Dokument.Metadata(
                    sakId = simulertRevurdering.sakId,
                    revurderingId = simulertRevurdering.id,
                    bestillBrev = true,
                )
            },
            anyOrNull(),
        )
        verify(mocks.identClient).hentNavnForNavIdent(argThat { it shouldBe simulertRevurdering.saksbehandler })
        verify(mocks.personService).hentPerson(argThat { it shouldBe simulertRevurdering.fnr })
        verify(mocks.oppgaveService).oppdaterOppgave(
            oppgaveId = argThat { it shouldBe simulertRevurdering.oppgaveId },
            beskrivelse = argThat { it shouldBe "Forhåndsvarsel er sendt." },
        )
        verify(mocks.sakService).hentSakForRevurdering(simulertRevurdering.id)
        verify(mocks.tilbakekrevingService).hentAvventerKravgrunnlag(any<UUID>())
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `velger forhåndsvarselbrev basert på underliggende revurdering`() {
        val simulertMedTilbakekreving = simulertRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt1000(
                    periode = år(2021),
                ),
            ),
            utbetalingerKjørtTilOgMed = 1.juli(2021),
        ).second

        val simulertUtenTilbakekreving = simulertRevurdering().second

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturnConsecutively listOf(
                    simulertMedTilbakekreving,
                    simulertUtenTilbakekreving,
                )
            },
            personService = mock {
                on { hentPerson(any()) } doReturn person(fnr, aktørId).right()
            },
            brevService = mock {
                on { lagBrev(any()) } doReturn "".toByteArray().right()
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
            },
        ).let {
            inOrder(
                it.brevService,
            ) {
                it.revurderingService.lagBrevutkastForForhåndsvarsling(
                    revurderingId = simulertMedTilbakekreving.id,
                    fritekst = "det kreves",
                )
                verify(it.brevService).lagBrev(
                    argThat {
                        it shouldBe LagBrevRequest.ForhåndsvarselTilbakekreving(
                            person = person(fnr, aktørId),
                            dagensDato = LocalDate.now(fixedClock),
                            saksnummer = simulertMedTilbakekreving.saksnummer,
                            saksbehandlerNavn = "saksbehandler",
                            fritekst = "det kreves",
                            bruttoTilbakekreving = simulertMedTilbakekreving.simulering.hentFeilutbetalteBeløp().sum(),
                            tilbakekreving = Tilbakekreving(simulertMedTilbakekreving.simulering.hentFeilutbetalteBeløp().månedbeløp),
                        )
                    },
                )
                it.revurderingService.lagBrevutkastForForhåndsvarsling(
                    revurderingId = simulertUtenTilbakekreving.id,
                    fritekst = "ikkeno kreving",
                )
                verify(it.brevService).lagBrev(
                    argThat {
                        it shouldBe LagBrevRequest.Forhåndsvarsel(
                            person = person(fnr, aktørId),
                            dagensDato = LocalDate.now(fixedClock),
                            saksnummer = simulertMedTilbakekreving.saksnummer,
                            saksbehandlerNavn = "saksbehandler",
                            fritekst = "ikkeno kreving",
                        )
                    },
                )
            }
        }
    }
}
