package no.nav.su.se.bakover.service.klage

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.IverksattKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteKlage
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.iverksattKlage
import no.nav.su.se.bakover.test.klageTilAttestering
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.påbegyntVilkårsvurdertKlage
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.underkjentKlage
import no.nav.su.se.bakover.test.utfyltVilkårsvurdertKlage
import no.nav.su.se.bakover.test.utfyltVurdertKlage
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class IverksettKlageTest {

    @Test
    fun `fant ikke klage`() {
        val sak = vedtakSøknadsbehandlingIverksattInnvilget().first
        val mocks = KlageServiceMocks(
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            },
        )

        val klageId = UUID.randomUUID()
        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.iverksett(
            sakId = sakId,
            klageId = klageId,
            attestant = attestant,
        ) shouldBe KunneIkkeIverksetteKlage.FantIkkeKlage.left()
        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke sak`() {
        val sakId = UUID.randomUUID()

        val mocks = KlageServiceMocks(
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn null
            },
        )

        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.iverksett(
            sakId = sakId,
            klageId = UUID.randomUUID(),
            attestant = attestant,
        ) shouldBe KunneIkkeIverksetteKlage.FantIkkeSak.left()
        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe sakId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kunne ikke lage brevrequest`() {

        val (sak, klage) = klageTilAttestering()
        val mocks = KlageServiceMocks(
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            },
            microsoftGraphApiMock = mock {
                on { hentNavnForNavIdent(any()) } doReturn MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
            },
        )

        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.iverksett(
            sakId = sakId,
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeIverksetteKlage.KunneIkkeLageBrevRequest.left()

        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(mocks.microsoftGraphApiMock).hentNavnForNavIdent(argThat { it shouldBe klage.saksbehandler })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Dokumentgenerering feilet`() {

        val (sak, klage) = klageTilAttestering()
        val person = person(fnr = sak.fnr)
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKnyttetVedtaksdato(any()) } doReturn 1.januar(2021)
            },
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            },
            microsoftGraphApiMock = mock {
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
        mocks.service.iverksett(
            sakId = sakId,
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeIverksetteKlage.DokumentGenereringFeilet.left()

        verify(mocks.klageRepoMock).hentKnyttetVedtaksdato(argThat { it shouldBe klage.id })
        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(mocks.microsoftGraphApiMock).hentNavnForNavIdent(argThat { it shouldBe klage.saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
        verify(mocks.brevServiceMock).lagBrev(
            argThat {
                it shouldBe LagBrevRequest.Klage.Oppretthold(
                    person = person,
                    dagensDato = fixedLocalDate,
                    saksbehandlerNavn = "Some name",
                    fritekst = klage.vurderinger.fritekstTilBrev,
                    hjemler = nonEmptyListOf(3, 4),
                    klageDato = 1.desember(2021),
                    vedtakDato = 1.januar(2021),
                )
            },
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Attestant og saksbehandler kan ikke være samme person`() {

        val (sak, klage) = klageTilAttestering()
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKnyttetVedtaksdato(any()) } doReturn 1.januar(2021)
            },
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            },
        )

        val attestant = NavIdentBruker.Attestant(klage.saksbehandler.navIdent)
        mocks.service.iverksett(
            sakId = sakId,
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeIverksetteKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Ugyldig tilstandsovergang fra opprettet`() {
        opprettetKlage().also {
            verifiserUgyldigTilstandsovergang(
                sak = it.first,
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vilkårsvurdering`() {
        påbegyntVilkårsvurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                sak = it.first,
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vilkårsvurdering`() {
        utfyltVilkårsvurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                sak = it.first,
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vilkårsvurdering`() {
        bekreftetVilkårsvurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                sak = it.first,
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vurdering`() {
        påbegyntVurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                sak = it.first,
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vurdering`() {
        utfyltVurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                sak = it.first,
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vurdering`() {
        bekreftetVurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                sak = it.first,
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang underkjent vurdering`() {
        underkjentKlage().also {
            verifiserUgyldigTilstandsovergang(
                sak = it.first,
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra iverksatt`() {
        iverksattKlage().also {
            verifiserUgyldigTilstandsovergang(
                sak = it.first,
                klage = it.second,
            )
        }
    }

    private fun verifiserUgyldigTilstandsovergang(
        klage: Klage,
        sak: Sak,
    ) {
        val mocks = KlageServiceMocks(
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            },
        )
        mocks.service.iverksett(
            sakId = sakId,
            klageId = klage.id,
            attestant = NavIdentBruker.Attestant("attestant"),
        ) shouldBe KunneIkkeIverksetteKlage.UgyldigTilstand(klage::class, IverksattKlage::class).left()

        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe klage.sakId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne iverksette klage som er til attestering`() {
        val (sak, klage) = klageTilAttestering()
        val journalpostIdForVedtak = JournalpostId(UUID.randomUUID().toString())
        val person = person(fnr = sak.fnr)
        val pdfAsBytes = "brevbytes".toByteArray()
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKnyttetVedtaksdato(any()) } doReturn 1.januar(2021)
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            },
            vedtakRepoMock = mock {
                on { hentJournalpostId(any()) } doReturn journalpostIdForVedtak
            },
            microsoftGraphApiMock = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Some name".right()
            },
            personServiceMock = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            brevServiceMock = mock {
                on { lagBrev(any()) } doReturn pdfAsBytes.right()
            },
            kabalClient = mock {
                on { sendTilKlageinstans(any(), any(), any()) } doReturn Unit.right()
            },
        )

        val attestant = NavIdentBruker.Attestant("s2")

        var expectedKlage: IverksattKlage?
        mocks.service.iverksett(
            sakId = sakId,
            klageId = klage.id,
            attestant = attestant,
        ).getOrHandle { fail(it.toString()) }.also {
            expectedKlage = IverksattKlage.create(
                id = it.id,
                opprettet = fixedTidspunkt,
                sakId = klage.sakId,
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                vilkårsvurderinger = klage.vilkårsvurderinger,
                vurderinger = klage.vurderinger,
                attesteringer = Attesteringshistorikk.create(
                    listOf(
                        Attestering.Iverksatt(
                            attestant = attestant,
                            opprettet = fixedTidspunkt,
                        ),
                    ),
                ),
                datoKlageMottatt = 1.desember(2021),
            )
            it shouldBe expectedKlage
        }

        verify(mocks.klageRepoMock).hentKnyttetVedtaksdato(argThat { it shouldBe klage.id })
        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(mocks.microsoftGraphApiMock).hentNavnForNavIdent(argThat { it shouldBe klage.saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
        verify(mocks.brevServiceMock).lagBrev(
            argThat {
                it shouldBe LagBrevRequest.Klage.Oppretthold(
                    person = person,
                    dagensDato = fixedLocalDate,
                    saksbehandlerNavn = "Some name",
                    fritekst = klage.vurderinger.fritekstTilBrev,
                    hjemler = nonEmptyListOf(3, 4),
                    klageDato = 1.desember(2021),
                    vedtakDato = 1.januar(2021),
                )
            },
        )
        verify(mocks.vedtakRepoMock).hentJournalpostId(argThat { it shouldBe klage.vilkårsvurderinger.vedtakId })
        verify(mocks.kabalClient).sendTilKlageinstans(
            argThat { it shouldBe expectedKlage },
            argThat { it shouldBe sak },
            argThat { it shouldBe journalpostIdForVedtak },
        )
        verify(mocks.brevServiceMock).lagreDokument(
            argThat {
                it shouldBe Dokument.MedMetadata.Informasjon(
                    utenMetadata = Dokument.UtenMetadata.Informasjon(
                        id = it.id,
                        opprettet = it.opprettet,
                        tittel = "Oversendelsesbrev til klager",
                        generertDokument = pdfAsBytes,
                        generertDokumentJson = "{\"personalia\":{\"dato\":\"01.01.2021\",\"fødselsnummer\":\"${sak.fnr}\",\"fornavn\":\"Tore\",\"etternavn\":\"Strømøy\"},\"saksbehandlerNavn\":\"Some name\",\"fritekst\":\"fritekstTilBrev\",\"hjemler\":[3,4],\"klageDato\":\"2021-12-01\",\"vedtakDato\":\"2021-01-01\"}",
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
