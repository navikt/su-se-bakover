package no.nav.su.se.bakover.service.klage

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.Klageinstanshendelser
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevForKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevRequest
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeTilKlageinstans
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.påbegyntVilkårsvurdertKlage
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.underkjentAvvistKlage
import no.nav.su.se.bakover.test.underkjentKlageTilVurdering
import no.nav.su.se.bakover.test.utfyltAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.utfyltVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.utfyltVurdertKlage
import no.nav.su.se.bakover.test.vurdertKlageTilAttestering
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.util.UUID

internal class OversendKlageTest {

    @Test
    fun `fant ikke klage`() {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )
        val klageId = UUID.randomUUID()
        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.oversend(
            klageId = klageId,
            attestant = attestant,
        ) shouldBe KunneIkkeOversendeKlage.FantIkkeKlage.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `en oversendtKlage er ikke en åpen klage`() {
        val klage = oversendtKlage().second
        klage.erÅpen() shouldBe false
    }

    @Test
    fun `Attestant og saksbehandler kan ikke være samme person`() {
        val klage = vurdertKlageTilAttestering().second
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn 1.januar(2021)
            },
        )
        val attestant = NavIdentBruker.Attestant(klage.saksbehandler.navIdent)
        mocks.service.oversend(
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeOversendeKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kunne ikke lage brevrequest`() {
        val klage = vurdertKlageTilAttestering().second
        val person = person(fnr = klage.fnr)
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
            personServiceMock = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn KunneIkkeHenteNavnForNavIdent.FantIkkeBrukerForNavIdent.left()
            },
        )

        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.oversend(
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe
            KunneIkkeOversendeKlage.KunneIkkeLageBrevRequest(
                KunneIkkeLageBrevRequest.FeilVedHentingAvSaksbehandlernavn(
                    KunneIkkeHenteNavnForNavIdent.FantIkkeBrukerForNavIdent,
                ),
            ).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe klage.fnr })
        verify(mocks.identClient).hentNavnForNavIdent(argThat { it shouldBe klage.saksbehandler })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Dokumentgenerering feilet`() {
        val (sak, klage) = vurdertKlageTilAttestering()
        val person = person(fnr = sak.fnr)
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn 1.januar(2021)
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Some name".right()
            },
            personServiceMock = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            brevServiceMock = mock {
                on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
            },
        )
        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.oversend(
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeOversendeKlage.KunneIkkeLageBrev(KunneIkkeLageBrevForKlage.KunneIkkeGenererePDF).left()

        verify(mocks.klageRepoMock).hentKnyttetVedtaksdato(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.identClient).hentNavnForNavIdent(argThat { it shouldBe klage.saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
        verify(mocks.brevServiceMock).lagBrev(
            argThat {
                it shouldBe LagBrevRequest.Klage.Oppretthold(
                    person = person,
                    dagensDato = fixedLocalDate,
                    saksbehandlerNavn = "Some name",
                    fritekst = klage.vurderinger.fritekstTilOversendelsesbrev,
                    klageDato = 1.desember(2021),
                    vedtakDato = 1.januar(2021),
                    saksnummer = Saksnummer(12345676),
                )
            },
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Fant ikke journalpost-id knyttet til vedtaket`() {
        val (sak, klage) = vurdertKlageTilAttestering()
        val person = person(fnr = sak.fnr)
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn 1.januar(2021)
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Some name".right()
            },
            personServiceMock = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            brevServiceMock = mock {
                on { lagBrev(any()) } doReturn "brevbytes".toByteArray().right()
            },
            vedtakServiceMock = mock {
                on { hentJournalpostId(any()) } doReturn null
            },
        )
        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.oversend(
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeOversendeKlage.FantIkkeJournalpostIdKnyttetTilVedtaket.left()

        verify(mocks.klageRepoMock).hentKnyttetVedtaksdato(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.identClient).hentNavnForNavIdent(argThat { it shouldBe klage.saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
        verify(mocks.brevServiceMock).lagBrev(
            argThat {
                it shouldBe LagBrevRequest.Klage.Oppretthold(
                    person = person,
                    dagensDato = fixedLocalDate,
                    saksbehandlerNavn = "Some name",
                    fritekst = klage.vurderinger.fritekstTilOversendelsesbrev,
                    klageDato = 1.desember(2021),
                    vedtakDato = 1.januar(2021),
                    saksnummer = Saksnummer(12345676),
                )
            },
        )
        verify(mocks.vedtakServiceMock).hentJournalpostId(argThat { it shouldBe klage.vilkårsvurderinger.vedtakId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Kunne ikke oversende til klageinstans`() {
        val (sak, klage) = vurdertKlageTilAttestering()
        val person = person(fnr = sak.fnr)
        val journalpostIdKnyttetTilVedtakDetKlagePå = JournalpostId("journalpostIdKnyttetTilVedtakDetKlagePå")
        val pdfAsBytes = "brevbytes".toByteArray()
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn 1.januar(2021)
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Some name".right()
            },
            personServiceMock = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            brevServiceMock = mock {
                on { lagBrev(any()) } doReturn pdfAsBytes.right()
            },
            vedtakServiceMock = mock {
                on { hentJournalpostId(any()) } doReturn journalpostIdKnyttetTilVedtakDetKlagePå
            },
            klageClient = mock {
                on { sendTilKlageinstans(any(), any()) } doReturn KunneIkkeOversendeTilKlageinstans.left()
            },
        )
        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.oversend(
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeOversendeKlage.KunneIkkeOversendeTilKlageinstans.left()

        verify(mocks.klageRepoMock).hentKnyttetVedtaksdato(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.identClient).hentNavnForNavIdent(argThat { it shouldBe klage.saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
        verify(mocks.brevServiceMock).lagBrev(
            argThat {
                it shouldBe LagBrevRequest.Klage.Oppretthold(
                    person = person,
                    dagensDato = fixedLocalDate,
                    saksbehandlerNavn = "Some name",
                    fritekst = klage.vurderinger.fritekstTilOversendelsesbrev,
                    klageDato = 1.desember(2021),
                    vedtakDato = 1.januar(2021),
                    saksnummer = sak.saksnummer,
                )
            },
        )
        verify(mocks.vedtakServiceMock).hentJournalpostId(argThat { it shouldBe klage.vilkårsvurderinger.vedtakId })
        val expectedKlage = OversendtKlage(
            forrigeSteg = klage,
            attesteringer = Attesteringshistorikk.create(
                listOf(
                    Attestering.Iverksatt(
                        attestant = attestant,
                        opprettet = fixedTidspunkt,
                    ),
                ),
            ),
            klageinstanshendelser = Klageinstanshendelser.empty(),
        )
        verify(mocks.klageClient).sendTilKlageinstans(
            klage = argThat { it shouldBe expectedKlage },
            journalpostIdForVedtak = argThat { it shouldBe journalpostIdKnyttetTilVedtakDetKlagePå },
        )
        verify(mocks.klageRepoMock).lagre(eq(expectedKlage), anyOrNull())
        verify(mocks.brevServiceMock).lagreDokument(
            argThat {
                it shouldBe Dokument.MedMetadata.Informasjon.Annet(
                    utenMetadata = Dokument.UtenMetadata.Informasjon.Annet(
                        id = it.id,
                        opprettet = it.opprettet,
                        tittel = "Oversendelsesbrev til klager",
                        generertDokument = pdfAsBytes,
                        generertDokumentJson = "{\"personalia\":{\"dato\":\"01.01.2021\",\"fødselsnummer\":\"${sak.fnr}\",\"fornavn\":\"Tore\",\"etternavn\":\"Strømøy\",\"saksnummer\":12345676},\"saksbehandlerNavn\":\"Some name\",\"fritekst\":\"fritekstTilBrev\",\"klageDato\":\"01.12.2021\",\"vedtakDato\":\"01.01.2021\",\"saksnummer\":12345676}",
                    ),
                    metadata = Dokument.Metadata(
                        sakId = sak.id,
                        søknadId = null,
                        vedtakId = null,
                        revurderingId = null,
                        klageId = klage.id,
                        bestillBrev = true,
                        journalpostId = null,
                        brevbestillingId = null,
                    ),
                )
            },
            argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Ugyldig tilstandsovergang fra opprettet`() {
        opprettetKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vilkårsvurdering`() {
        påbegyntVilkårsvurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vilkårsvurdering`() {
        utfyltVilkårsvurdertKlageTilVurdering().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vilkårsvurdering avvist`() {
        utfyltAvvistVilkårsvurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vilkårsvurdering til vurdering`() {
        bekreftetVilkårsvurdertKlageTilVurdering().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vilkårsvurdering avvist`() {
        bekreftetAvvistVilkårsvurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vurdering`() {
        påbegyntVurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vurdering`() {
        utfyltVurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vurdering`() {
        bekreftetVurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang underkjent vurdering`() {
        underkjentKlageTilVurdering().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang underkjent vurdering avvist`() {
        underkjentAvvistKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra iverksatt`() {
        oversendtKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra avvist`() {
        iverksattAvvistKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    private fun verifiserUgyldigTilstandsovergang(klage: Klage) {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
        )
        mocks.service.oversend(
            klageId = klage.id,
            attestant = NavIdentBruker.Attestant("attestant"),
        ) shouldBe KunneIkkeOversendeKlage.UgyldigTilstand(klage::class).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne oversende en klage som er til attestering`() {
        val (sak, klage) = vurdertKlageTilAttestering()
        val journalpostIdForVedtak = JournalpostId(UUID.randomUUID().toString())
        val person = person(fnr = sak.fnr)
        val pdfAsBytes = "brevbytes".toByteArray()
        val observerMock: EventObserver = mock { on { handle(any()) }.then {} }
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn 1.januar(2021)
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            vedtakServiceMock = mock {
                on { hentJournalpostId(any()) } doReturn journalpostIdForVedtak
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Some name".right()
            },
            personServiceMock = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            brevServiceMock = mock {
                on { lagBrev(any()) } doReturn pdfAsBytes.right()
            },
            klageClient = mock {
                on { sendTilKlageinstans(any(), any()) } doReturn Unit.right()
            },
            oppgaveService = mock {
                on { lukkOppgave(any()) } doReturn Unit.right()
            },
            observer = observerMock
        )
        val attestant = NavIdentBruker.Attestant("s2")

        var expectedKlage: OversendtKlage?
        mocks.service.oversend(
            klageId = klage.id,
            attestant = attestant,
        ).getOrHandle { fail(it.toString()) }.also {
            expectedKlage = OversendtKlage(
                forrigeSteg = klage,
                attesteringer = Attesteringshistorikk.create(
                    listOf(
                        Attestering.Iverksatt(
                            attestant = attestant,
                            opprettet = fixedTidspunkt,
                        ),
                    ),
                ),
                klageinstanshendelser = Klageinstanshendelser.empty(),
            )
            it shouldBe expectedKlage
            verify(observerMock).handle(argThat { actual -> Event.Statistikk.Klagestatistikk.Oversendt(it) shouldBe actual })
        }

        verify(mocks.klageRepoMock).hentKnyttetVedtaksdato(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.identClient).hentNavnForNavIdent(argThat { it shouldBe klage.saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
        verify(mocks.brevServiceMock).lagBrev(
            argThat {
                it shouldBe LagBrevRequest.Klage.Oppretthold(
                    person = person,
                    dagensDato = fixedLocalDate,
                    saksbehandlerNavn = "Some name",
                    fritekst = klage.vurderinger.fritekstTilOversendelsesbrev,
                    klageDato = 1.desember(2021),
                    vedtakDato = 1.januar(2021),
                    saksnummer = klage.saksnummer,
                )
            },
        )
        verify(mocks.vedtakServiceMock).hentJournalpostId(argThat { it shouldBe klage.vilkårsvurderinger.vedtakId })
        verify(mocks.klageClient).sendTilKlageinstans(
            klage = argThat { it shouldBe expectedKlage },
            journalpostIdForVedtak = argThat { it shouldBe journalpostIdForVedtak },
        )
        verify(mocks.brevServiceMock).lagreDokument(
            argThat {
                it shouldBe Dokument.MedMetadata.Informasjon.Annet(
                    utenMetadata = Dokument.UtenMetadata.Informasjon.Annet(
                        id = it.id,
                        opprettet = it.opprettet,
                        tittel = "Oversendelsesbrev til klager",
                        generertDokument = pdfAsBytes,
                        generertDokumentJson = "{\"personalia\":{\"dato\":\"01.01.2021\",\"fødselsnummer\":\"${sak.fnr}\",\"fornavn\":\"Tore\",\"etternavn\":\"Strømøy\",\"saksnummer\":12345676},\"saksbehandlerNavn\":\"Some name\",\"fritekst\":\"fritekstTilBrev\",\"klageDato\":\"01.12.2021\",\"vedtakDato\":\"01.01.2021\",\"saksnummer\":12345676}",
                    ),
                    metadata = Dokument.Metadata(
                        sakId = sak.id,
                        søknadId = null,
                        vedtakId = null,
                        revurderingId = null,
                        klageId = klage.id,
                        bestillBrev = true,
                        journalpostId = null,
                        brevbestillingId = null,
                    ),

                )
            },
            argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(mocks.klageRepoMock).lagre(
            argThat { it shouldBe expectedKlage },
            argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(mocks.oppgaveService).lukkOppgave(klage.oppgaveId)
        mocks.verifyNoMoreInteractions()
    }
}
