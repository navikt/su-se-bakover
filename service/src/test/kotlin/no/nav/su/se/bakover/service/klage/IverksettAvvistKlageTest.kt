package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteAvvistKlage
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.avvistKlageTilAttestering
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
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
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

internal class IverksettAvvistKlageTest {

    @Test
    fun `fant ikke klage`() {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )

        val klageId = UUID.randomUUID()
        val attestant = NavIdentBruker.Attestant("attestantensen")

        mocks.service.iverksettAvvistKlage(
            klageId,
            attestant,
        ) shouldBe KunneIkkeIverksetteAvvistKlage.FantIkkeKlage.left()
        Mockito.verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Attestant og saksbehandler kan ikke være samme person`() {
        val klage = avvistKlageTilAttestering().second
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn 1.januar(2021)
            },
        )
        val attestant = NavIdentBruker.Attestant(klage.saksbehandler.navIdent)
        mocks.service.iverksettAvvistKlage(
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeIverksetteAvvistKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        Mockito.verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `er ikke åpen`() {
        val klage = iverksattAvvistKlage().second
        klage.erÅpen() shouldBe false
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
    fun `Ugyldig tilstandsovergang fra utfylt vilkårsvurdering til vurdering`() {
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
    fun `Ugyldig tilstandsovergang fra bekreftet vilkårsvurdering`() {
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
    fun `Ugyldig tilstandsovergang underkjent vurdering til vurdering`() {
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
    fun `Ugyldig tilstandsovergang fra oversendt`() {
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
        mocks.service.iverksettAvvistKlage(
            klageId = klage.id,
            attestant = NavIdentBruker.Attestant("attestant"),
        ) shouldBe KunneIkkeIverksetteAvvistKlage.UgyldigTilstand(klage::class).left()

        Mockito.verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan iverksette en klage som er til attestering avvist`() {
        val (_, klage) = avvistKlageTilAttestering(fritekstTilBrev = "dette er min fritekst")
        val attestant = NavIdentBruker.Attestant("attestant")
        val person = person(fnr = klage.fnr)
        val dokument = "myDoc".toByteArray()
        val observerMock: StatistikkEventObserver = mock { on { handle(any()) }.then {} }
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn 1.januar(2022)
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            brevServiceMock = mock {
                on { lagBrev(any()) } doReturn dokument.right()
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Johnny".right()
            },
            personServiceMock = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            oppgaveService = mock {
                on { lukkOppgave(any()) } doReturn Unit.right()
            },
            vedtakServiceMock = mock {
                doNothing().whenever(it).lagre(any())
            },
            observer = observerMock,
        )

        val actual = mocks.service.iverksettAvvistKlage(klage.id, attestant).getOrFail()

        val expected = IverksattAvvistKlage(
            forrigeSteg = klage,
            attesteringer = Attesteringshistorikk.create(
                listOf(
                    Attestering.Iverksatt(
                        attestant = attestant,
                        opprettet = fixedTidspunkt,
                    ),
                ),
            ),
        )

        actual shouldBe expected

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.identClient).hentNavnForNavIdent(argThat { it shouldBe klage.saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe klage.fnr })
        verify(mocks.brevServiceMock).lagBrev(
            argThat {
                it shouldBe LagBrevRequest.Klage.Avvist(
                    person = person,
                    dagensDato = fixedLocalDate,
                    saksbehandlerNavn = "Johnny",
                    fritekst = "dette er min fritekst",
                    saksnummer = klage.saksnummer,
                )
            },
        )
        var expectedVedtak: Klagevedtak.Avvist? = null
        verify(mocks.vedtakServiceMock).lagre(
            argThat {
                expectedVedtak = Klagevedtak.Avvist(
                    id = it.id,
                    opprettet = fixedTidspunkt,
                    saksbehandler = expected.saksbehandler,
                    attestant = expected.attesteringer.first().attestant,
                    klage = expected,
                )
                it shouldBe expectedVedtak!!
            },
        )
        verify(mocks.brevServiceMock).lagreDokument(
            argThat {
                it shouldBe Dokument.MedMetadata.Vedtak(
                    utenMetadata = Dokument.UtenMetadata.Vedtak(
                        id = it.id,
                        opprettet = it.opprettet,
                        tittel = "Avvist klage",
                        generertDokument = dokument,
                        generertDokumentJson = "{\"personalia\":{\"dato\":\"01.01.2021\",\"fødselsnummer\":\"${klage.fnr}\",\"fornavn\":\"Tore\",\"etternavn\":\"Strømøy\",\"saksnummer\":${klage.saksnummer}},\"saksbehandlerNavn\":\"Johnny\",\"fritekst\":\"dette er min fritekst\",\"saksnummer\":${klage.saksnummer},\"erAldersbrev\":false}",
                    ),
                    metadata = Dokument.Metadata(
                        sakId = klage.sakId,
                        klageId = klage.id,
                        vedtakId = expectedVedtak!!.id,
                        bestillBrev = true,
                    ),
                )
            },
            argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(mocks.klageRepoMock).lagre(
            argThat { it shouldBe expected },
            argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(mocks.oppgaveService).lukkOppgave(argThat { it shouldBe expected.oppgaveId })
        verify(observerMock).handle(
            argThat {
                it shouldBe StatistikkEvent.Behandling.Klage.Avvist(
                    Klagevedtak.Avvist.fromIverksattAvvistKlage(
                        iverksattAvvistKlage = actual,
                        clock = fixedClock,
                    ).copy(
                        id = (it as StatistikkEvent.Behandling.Klage.Avvist).vedtak.id,
                    ),
                )
            },
        )
        mocks.verifyNoMoreInteractions()
    }
}
