package no.nav.su.se.bakover.service.klage

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.Klageinstanshendelser
import no.nav.su.se.bakover.domain.klage.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.avvistKlageTilAttestering
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.påbegyntVilkårsvurdertKlage
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.underkjentAvvistKlage
import no.nav.su.se.bakover.test.underkjentKlageTilVurdering
import no.nav.su.se.bakover.test.utfyltVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.utfyltVurdertKlage
import no.nav.su.se.bakover.test.vurdertKlageTilAttestering
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class UnderkjennKlageTest {

    @Test
    fun `fant ikke klage`() {

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )

        val klageId = UUID.randomUUID()
        val attestant = NavIdentBruker.Attestant("s2")
        val request = UnderkjennKlageRequest(
            klageId = klageId,
            attestant = attestant,
            grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
            kommentar = "underkjennelseskommentar",
        )
        mocks.service.underkjenn(request) shouldBe KunneIkkeUnderkjenne.FantIkkeKlage.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kunne ikke hente aktør id`() {
        val klage = vurdertKlageTilAttestering().second
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
            personServiceMock = mock {
                on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.Ukjent.left()
            },
        )

        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.underkjenn(
            UnderkjennKlageRequest(
                klageId = klage.id,
                attestant = attestant,
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "",
            ),
        ) shouldBe KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.personServiceMock).hentAktørId(argThat { it shouldBe klage.fnr })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kunne ikke opprette oppgave`() {
        val klage = vurdertKlageTilAttestering().second
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
            personServiceMock = mock {
                on { hentAktørId(any()) } doReturn AktørId("aktørId").right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveFeil.KunneIkkeOppretteOppgave.left()
            },
        )

        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.underkjenn(
            UnderkjennKlageRequest(
                klageId = klage.id,
                attestant = attestant,
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "",
            ),
        ) shouldBe KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.oppgaveService).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Klage.Saksbehandler(
                    saksnummer = klage.saksnummer,
                    aktørId = AktørId("aktørId"),
                    journalpostId = klage.journalpostId,
                    tilordnetRessurs = klage.saksbehandler,
                    clock = fixedClock,
                )
            },
        )
        verify(mocks.personServiceMock).hentAktørId(argThat { it shouldBe klage.fnr })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Attestant og saksbehandler kan ikke være samme person`() {
        val (_, klage) = vurdertKlageTilAttestering()
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
        )
        val attestant = NavIdentBruker.Attestant(klage.saksbehandler.navIdent)
        mocks.service.underkjenn(
            UnderkjennKlageRequest(
                klageId = klage.id,
                attestant = attestant,
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "",
            ),
        ) shouldBe KunneIkkeUnderkjenne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `en underkjent(Vurdert) klage er en åpen klage`() {
        val klage = underkjentKlageTilVurdering().second
        klage.erÅpen() shouldBe true
    }

    @Test
    fun `en underkjent(avvist) klage er en åpen klage`() {
        val klage = underkjentAvvistKlage().second
        klage.erÅpen() shouldBe true
    }

    @Test
    fun `Ugyldig tilstandsovergang fra opprettet`() {
        verifiserUgyldigTilstandsovergang(
            klage = opprettetKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vilkårsvurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = påbegyntVilkårsvurdertKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vilkårsvurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = utfyltVilkårsvurdertKlageTilVurdering().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vilkårsvurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = bekreftetVilkårsvurdertKlageTilVurdering().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = påbegyntVurdertKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = utfyltVurdertKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = bekreftetVurdertKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang underkjent vurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = underkjentKlageTilVurdering().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra iverksatt`() {
        verifiserUgyldigTilstandsovergang(
            klage = oversendtKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra avvist`() {
        verifiserUgyldigTilstandsovergang(
            klage = iverksattAvvistKlage().second,
        )
    }

    private fun verifiserUgyldigTilstandsovergang(
        klage: Klage,
    ) {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
        )
        val attestant = NavIdentBruker.Attestant("attestant")
        val request = UnderkjennKlageRequest(
            klageId = klage.id,
            attestant = attestant,
            grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
            kommentar = "underkjennelseskommentar",
        )
        mocks.service.underkjenn(request) shouldBe KunneIkkeUnderkjenne.UgyldigTilstand(
            klage::class,
            VurdertKlage.Bekreftet::class,
        ).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne underkjenne klage som er til attestering`() {
        val klage = vurdertKlageTilAttestering().second
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            personServiceMock = mock {
                on { hentAktørId(any()) } doReturn AktørId("aktørId").right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("nyOppgaveId").right()
            },
        )

        val attestant = NavIdentBruker.Attestant("s2")

        var expectedKlage: VurdertKlage.Bekreftet?
        val request = UnderkjennKlageRequest(
            klageId = klage.id,
            attestant = attestant,
            grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
            kommentar = "underkjennelseskommentar",
        )
        mocks.service.underkjenn(request).getOrHandle { throw RuntimeException(it.toString()) }.also {
            expectedKlage = VurdertKlage.Bekreftet.create(
                id = it.id,
                opprettet = fixedTidspunkt,
                sakId = klage.sakId,
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = OppgaveId("nyOppgaveId"),
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                vilkårsvurderinger = klage.vilkårsvurderinger,
                vurderinger = klage.vurderinger,
                attesteringer = Attesteringshistorikk.create(
                    listOf(
                        Attestering.Underkjent(
                            attestant = attestant,
                            opprettet = fixedTidspunkt,
                            grunn = request.grunn,
                            kommentar = request.kommentar,
                        ),
                    ),
                ),
                datoKlageMottatt = 1.desember(2021),
                klageinstanshendelser = Klageinstanshendelser.empty()
            )
            it shouldBe expectedKlage
        }
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).defaultTransactionContext()
        verify(mocks.klageRepoMock).lagre(argThat { it shouldBe expectedKlage }, argThat { it shouldBe TestSessionFactory.transactionContext })
        verify(mocks.oppgaveService).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Klage.Saksbehandler(
                    saksnummer = klage.saksnummer,
                    aktørId = AktørId("aktørId"),
                    journalpostId = klage.journalpostId,
                    tilordnetRessurs = klage.saksbehandler,
                    clock = fixedClock,
                )
            },
        )
        verify(mocks.personServiceMock).hentAktørId(argThat { it shouldBe klage.fnr })
        verify(mocks.oppgaveService).lukkOppgave(klage.oppgaveId)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `skal kunne underkjenne en avvist klage til attestering`() {
        val klage = avvistKlageTilAttestering().second
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            personServiceMock = mock {
                on { hentAktørId(any()) } doReturn AktørId("aktørId").right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("nyOppgaveId").right()
            },
        )

        val attestant = NavIdentBruker.Attestant("s2")

        var expectedKlage: AvvistKlage?
        val request = UnderkjennKlageRequest(
            klageId = klage.id,
            attestant = attestant,
            grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
            kommentar = "underkjennelseskommentar",
        )
        mocks.service.underkjenn(request).getOrHandle { throw RuntimeException(it.toString()) }.also {
            expectedKlage = AvvistKlage.create(
                forrigeSteg = VilkårsvurdertKlage.Bekreftet.Avvist.create(
                    id = it.id,
                    opprettet = fixedTidspunkt,
                    sakId = klage.sakId,
                    saksnummer = klage.saksnummer,
                    fnr = klage.fnr,
                    journalpostId = klage.journalpostId,
                    oppgaveId = OppgaveId("nyOppgaveId"),
                    saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                    vilkårsvurderinger = klage.vilkårsvurderinger,
                    attesteringer = Attesteringshistorikk.create(
                        listOf(
                            Attestering.Underkjent(
                                attestant = attestant,
                                opprettet = fixedTidspunkt,
                                grunn = request.grunn,
                                kommentar = request.kommentar,
                            ),
                        ),
                    ),
                    datoKlageMottatt = 1.desember(2021),
                ),
                fritekstTilBrev = "dette er en fritekst med person opplysninger",
            )
            it shouldBe expectedKlage
        }
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).defaultTransactionContext()
        verify(mocks.klageRepoMock).lagre(argThat { it shouldBe expectedKlage }, argThat { it shouldBe TestSessionFactory.transactionContext })
        verify(mocks.oppgaveService).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Klage.Saksbehandler(
                    saksnummer = klage.saksnummer,
                    aktørId = AktørId("aktørId"),
                    journalpostId = klage.journalpostId,
                    tilordnetRessurs = klage.saksbehandler,
                    clock = fixedClock,
                )
            },
        )
        verify(mocks.personServiceMock).hentAktørId(argThat { it shouldBe klage.fnr })
        verify(mocks.oppgaveService).lukkOppgave(klage.oppgaveId)
        mocks.verifyNoMoreInteractions()
    }
}
