package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.Klageinstanshendelser
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.avvistKlageTilAttestering
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
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

internal class SendKlageTilAttesteringTest {

    @Test
    fun `fant ikke klage`() {

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )

        val klageId = UUID.randomUUID()
        val saksbehandler = NavIdentBruker.Saksbehandler("s2")
        mocks.service.sendTilAttestering(
            klageId = klageId,
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeSendeTilAttestering.FantIkkeKlage.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kunne ikke hente aktør id`() {
        val klage = bekreftetVurdertKlage().second
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
            personServiceMock = mock {
                on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.Ukjent.left()
            },
        )

        val saksbehandler = NavIdentBruker.Saksbehandler("s2")
        mocks.service.sendTilAttestering(
            klageId = klage.id,
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.personServiceMock).hentAktørId(argThat { it shouldBe klage.fnr })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kunne ikke opprette oppgave`() {
        val klage = bekreftetVurdertKlage().second
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

        val saksbehandler = NavIdentBruker.Saksbehandler("s2")
        mocks.service.sendTilAttestering(
            klageId = klage.id,
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.oppgaveService).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Klage.Saksbehandler(
                    saksnummer = klage.saksnummer,
                    aktørId = AktørId("aktørId"),
                    journalpostId = klage.journalpostId,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                )
            },
        )
        verify(mocks.personServiceMock).hentAktørId(argThat { it shouldBe klage.fnr })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `en klageTilAttestering(Vurdert) er en åpen klage`() {
        val klage = vurdertKlageTilAttestering().second
        klage.erÅpen() shouldBe true
    }

    @Test
    fun `en klageTilAttestering(avvist) er en åpen klage`() {
        val klage = avvistKlageTilAttestering().second
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
    fun `Ugyldig tilstandsovergang fra utfylt vilkårsvurdering til vurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = utfyltVilkårsvurdertKlageTilVurdering().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vilkårsvurdering til vurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = bekreftetVilkårsvurdertKlageTilVurdering().second,
        )
    }

    @Test
    fun `ugyldig statusovergang fra bekreftet avvist vilkårsvurdert klage til attestering`() {
        bekreftetAvvistVilkårsvurdertKlage().let {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
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
    fun `Ugyldig tilstandsovergang fra til attestering`() {
        verifiserUgyldigTilstandsovergang(
            klage = vurdertKlageTilAttestering().second,
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
        mocks.service.sendTilAttestering(
            klageId = klage.id,
            saksbehandler = klage.saksbehandler,
        ) shouldBe KunneIkkeSendeTilAttestering.UgyldigTilstand(klage::class, KlageTilAttestering::class).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne sende en bekreftet vurdert klage til attestering`() {
        bekreftetVurdertKlage().let {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
            )
        }
    }

    @Test
    fun `Skal kunne sende en underkjent til vurdering klage til attestering`() {
        underkjentKlageTilVurdering().let {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                attesteringer = it.second.attesteringer,
                tilordnetRessurs = it.second.attesteringer.let {
                    assert(it.size == 1)
                    it.first().attestant
                },
            )
        }
    }

    @Test
    fun `skal kunne sende en underkjent avvist klage klage til attestering`() {
        underkjentAvvistKlage().let {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                attesteringer = it.second.attesteringer,
                tilordnetRessurs = it.second.attesteringer.let {
                    assert(it.size == 1)
                    it.first().attestant
                },
                fritekstTilBrev = it.second.fritekstTilBrev,
            )
        }
    }

    private fun verifiserGyldigStatusovergang(
        vedtak: Vedtak,
        klage: Klage,
        attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty(),
        tilordnetRessurs: NavIdentBruker.Attestant? = null,
        fritekstTilBrev: String = "attesterings fritekst for brevet",
    ) {
        val session = TestSessionFactory()

        session.withTransactionContext { transactionContext ->

            val mocks = KlageServiceMocks(
                klageRepoMock = mock {
                    on { hentKlage(any()) } doReturn klage
                    on { defaultTransactionContext() } doReturn transactionContext
                },
                vedtakServiceMock = mock {
                    on { hentForVedtakId(any()) } doReturn vedtak
                },
                personServiceMock = mock {
                    on { hentAktørId(any()) } doReturn AktørId("aktørId").right()
                },
                oppgaveService = mock {
                    on { opprettOppgave(any()) } doReturn OppgaveId("nyOppgaveId").right()
                },
            )

            var expectedKlage: KlageTilAttestering?
            mocks.service.sendTilAttestering(
                klageId = klage.id,
                saksbehandler = NavIdentBruker.Saksbehandler("bekreftetVilkårsvurderingene"),
            ).orNull()!!.also {
                expectedKlage = KlageTilAttestering.create(
                    id = it.id,
                    opprettet = fixedTidspunkt,
                    sakId = klage.sakId,
                    saksnummer = klage.saksnummer,
                    fnr = klage.fnr,
                    journalpostId = klage.journalpostId,
                    oppgaveId = OppgaveId("nyOppgaveId"),
                    saksbehandler = NavIdentBruker.Saksbehandler("bekreftetVilkårsvurderingene"),
                    vilkårsvurderinger = when (klage) {
                        is AvvistKlage -> klage.vilkårsvurderinger
                        is VurdertKlage.Bekreftet -> klage.vilkårsvurderinger
                        else -> throw IllegalStateException("Bare VilkårsvurdertKlage.Bekreftet(Avvist), og VurdertKlage.Bekreftet kan sendes til attestering")
                    },
                    vurderinger = when (klage) {
                        is AvvistKlage -> null
                        is VurdertKlage.Bekreftet -> klage.vurderinger
                        else -> throw IllegalStateException("Bare VilkårsvurdertKlage.Bekreftet(Avvist), og VurdertKlage.Bekreftet kan sendes til attestering")
                    },
                    attesteringer = attesteringer,
                    datoKlageMottatt = 1.desember(2021),
                    fritekstTilBrev = fritekstTilBrev,
                    klageinstanshendelser = Klageinstanshendelser.empty()
                )
                it shouldBe expectedKlage
            }

            verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
            verify(mocks.klageRepoMock).lagre(
                argThat {
                    it shouldBe expectedKlage
                },
                argThat { it shouldBe transactionContext },
            )
            verify(mocks.klageRepoMock).defaultTransactionContext()
            verify(mocks.oppgaveService).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Klage.Saksbehandler(
                        saksnummer = klage.saksnummer,
                        aktørId = AktørId("aktørId"),
                        journalpostId = klage.journalpostId,
                        tilordnetRessurs = tilordnetRessurs,
                        clock = fixedClock,
                    )
                },
            )
            verify(mocks.personServiceMock).hentAktørId(argThat { it shouldBe klage.fnr })
            verify(mocks.oppgaveService).lukkOppgave(klage.oppgaveId)
            mocks.verifyNoMoreInteractions()
        }
    }
}
