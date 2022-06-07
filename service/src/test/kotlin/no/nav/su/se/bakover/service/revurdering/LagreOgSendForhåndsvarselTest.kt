package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.beregning.Tilbakekreving
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksbehandlerNavn
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
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
        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
        ).second

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
                on { hentPerson(any()) } doReturn BehandlingTestUtils.person.right()
            },
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
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
                    person = BehandlingTestUtils.person,
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
        verify(mocks.tilbakekrevingService).hentAvventerKravgrunnlag(any<UUID>())
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `forhåndsvarsler ikke en allerede forhåndsvarslet revurdering`() {
        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt,
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
        ).second

        RevurderingTestUtils.createRevurderingService(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            personService = mock {
                on { hentPerson(any()) } doReturn BehandlingTestUtils.person.right()
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
            },
        ).lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.UgyldigTilstandsovergangForForhåndsvarsling.left()
    }

    @Test
    fun `kan endre fra ingen forhåndsvarsel til forhåndsvarsel`() {
        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
        ).second

        val mocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            personService = mock {
                on { hentPerson(any()) } doReturn BehandlingTestUtils.person.right()
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
        verify(mocks.tilbakekrevingService).hentAvventerKravgrunnlag(any<UUID>())
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `får ikke sendt forhåndsvarsel dersom det ikke er mulig å sende behandlingen videre til attestering`() {
        val simuleringMock = mock<Simulering> {
            on { harFeilutbetalinger() } doReturn true
        }
        val simulertRevurdering = RevurderingTestUtils.simulertRevurderingInnvilget.copy(
            simulering = simuleringMock,
            forhåndsvarsel = null,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.Attestering(
            KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke,
        ).left()
    }

    private fun testForhåndsvarslerIkkeGittRevurdering(revurdering: Revurdering) {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurdering
        }

        RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.MåVæreITilstandenSimulert(
            revurdering::class,
        ).left()
    }

    @Test
    fun `forhåndsvarsler bare simulerte revurderinger`() {
        val opprettet = OpprettetRevurdering(
            id = revurderingId,
            periode = RevurderingTestUtils.periodeNesteMånedOgTreMånederFram,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = RevurderingTestUtils.revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            avkorting = AvkortingVedRevurdering.Uhåndtert.IngenUtestående,
        )
        testForhåndsvarslerIkkeGittRevurdering(opprettet)

        val beregnet = BeregnetRevurdering.Innvilget(
            id = revurderingId,
            periode = RevurderingTestUtils.periodeNesteMånedOgTreMånederFram,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = RevurderingTestUtils.revurderingsårsak,
            forhåndsvarsel = null,
            beregning = TestBeregning,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående,
        )
        testForhåndsvarslerIkkeGittRevurdering(beregnet)
    }

    @Test
    fun `forhåndsvarsel - hent person feilet`() {
        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            forhåndsvarsel = null,
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
        ).second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
        ).lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.FantIkkePerson.left()
    }

    @Test
    fun `forhåndsvarsel - generering av dokument feiler`() {
        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            forhåndsvarsel = null,
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
        ).second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
            on { hentPerson(any()) } doReturn BehandlingTestUtils.person.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }

        val microsoftGraphApiClientMock = mock<IdentClient> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
        }

        RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            brevService = brevServiceMock,
            identClient = microsoftGraphApiClientMock,
        ).lagreOgSendForhåndsvarsel(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            forhåndsvarselhandling = Forhåndsvarselhandling.FORHÅNDSVARSLE,
            fritekst = "",
        ) shouldBe KunneIkkeForhåndsvarsle.KunneIkkeGenerereDokument.left()

        verify(revurderingRepoMock, never()).lagre(any(), anyOrNull())
        verify(brevServiceMock, never()).lagreDokument(any())
    }

    @Test
    fun `forhåndsvarsel - oppdatering av oppgave feiler`() {
        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
        ).second
        val mocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
                on { hentPerson(any()) } doReturn BehandlingTestUtils.person.right()
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
                on { hentPerson(any()) } doReturn BehandlingTestUtils.person.right()
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
                            person = BehandlingTestUtils.person,
                            dagensDato = LocalDate.now(fixedClock),
                            saksnummer = simulertMedTilbakekreving.saksnummer,
                            saksbehandlerNavn = "saksbehandler",
                            fritekst = "det kreves",
                            bruttoTilbakekreving = simulertMedTilbakekreving.simulering.hentFeilutbetalteBeløp().sum(),
                            tilbakekreving = Tilbakekreving(simulertMedTilbakekreving.simulering.hentFeilutbetalteBeløp().månedbeløp)
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
                            person = BehandlingTestUtils.person,
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
