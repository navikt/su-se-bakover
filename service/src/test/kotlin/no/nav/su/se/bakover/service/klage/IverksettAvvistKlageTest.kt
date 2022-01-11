package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteAvvistKlage
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.avvistKlageTilAttestering
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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

        mocks.service.avvis(klageId, attestant) shouldBe KunneIkkeIverksetteAvvistKlage.FantIkkeKlage.left()
        Mockito.verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Attestant og saksbehandler kan ikke være samme person`() {
        val klage = avvistKlageTilAttestering().second
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn 1.januar(2021)
            },
        )
        val attestant = NavIdentBruker.Attestant(klage.saksbehandler.navIdent)
        mocks.service.avvis(
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeIverksetteAvvistKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        Mockito.verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
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
        mocks.service.avvis(
            klageId = klage.id,
            attestant = NavIdentBruker.Attestant("attestant"),
        ) shouldBe KunneIkkeIverksetteAvvistKlage.UgyldigTilstand(klage::class, IverksattAvvistKlage::class).left()

        Mockito.verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan iverksette en klage som er til attestering avvist`() {

        val (_, klage) = avvistKlageTilAttestering()
        val attestant = NavIdentBruker.Attestant("attestant")

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
            oppgaveService = mock {
                on { lukkOppgave(any()) } doReturn Unit.right()
            },
        )

        val actual = mocks.service.avvis(klage.id, attestant).getOrFail()

        val expected = IverksattAvvistKlage.create(
            id = klage.id,
            opprettet = klage.opprettet,
            sakId = klage.sakId,
            saksnummer = klage.saksnummer,
            fnr = klage.fnr,
            journalpostId = klage.journalpostId,
            oppgaveId = klage.oppgaveId,
            saksbehandler = klage.saksbehandler,
            vilkårsvurderinger = klage.vilkårsvurderinger,
            vurderinger = null,
            attesteringer = Attesteringshistorikk.create(
                listOf(
                    Attestering.Iverksatt(
                        attestant = attestant,
                        opprettet = fixedTidspunkt,
                    ),
                ),
            ),
            datoKlageMottatt = klage.datoKlageMottatt,
        )

        actual shouldBe expected

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.oppgaveService).lukkOppgave(argThat { it shouldBe expected.oppgaveId })
    }
}
