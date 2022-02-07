package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeAvslutteKlage
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.avsluttetKlage
import no.nav.su.se.bakover.test.avvistKlage
import no.nav.su.se.bakover.test.avvistKlageTilAttestering
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.påbegyntVilkårsvurdertKlage
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.utfyltAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.utfyltVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.utfyltVurdertKlage
import no.nav.su.se.bakover.test.vurdertKlageTilAttestering
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.UUID

internal class AvsluttKlageTest {

    @Test
    fun `fant ikke klage - lagrer ikke`() {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )
        val klageId = UUID.randomUUID()
        mocks.service.avslutt(
            klageId = klageId,
            saksbehandler = NavIdentBruker.Saksbehandler("Saksbehandler som prøvde og avslutte klagen"),
            begrunnelse = "Begrunnelse for å avslutte klagen",
        ) shouldBe KunneIkkeAvslutteKlage.FantIkkeKlage.left()

        verify(mocks.klageRepoMock, times(0)).lagre(any(), anyOrNull())
    }

    @Test
    fun `Ugyldig tilstandsovergang fra avsluttet klage - lagrer ikke`() {
        verifiserUgyldigTilstandsovergang(
            klage = avsluttetKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra vurdert klage til attestering - lagrer ikke`() {
        verifiserUgyldigTilstandsovergang(
            klage = vurdertKlageTilAttestering().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra avvist klage til attestering - lagrer ikke`() {
        verifiserUgyldigTilstandsovergang(
            klage = avvistKlageTilAttestering().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra oversendt klage - lagrer ikke`() {
        verifiserUgyldigTilstandsovergang(
            klage = oversendtKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra iverksatt avvist klage - lagrer ikke`() {
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
        mocks.service.avslutt(
            klageId = klage.id,
            saksbehandler = NavIdentBruker.Saksbehandler("Saksbehandler som avsluttet klagen"),
            begrunnelse = "Begrunnelse for å avslutte klagen",
        ) shouldBe KunneIkkeAvslutteKlage.UgyldigTilstand(klage::class).left()

        verify(mocks.klageRepoMock, times(0)).lagre(any(), anyOrNull())
    }

    @Test
    fun `kan avslutte opprettet klage`() {
        kanAvslutteKlage(opprettetKlage().second)
    }

    @Test
    fun `kan avslutte påbegynt vilkårsvurdert klage`() {
        kanAvslutteKlage(påbegyntVilkårsvurdertKlage().second)
    }

    @Test
    fun `kan avslutte utfylt vilkårsvurdert klage til vurdering`() {
        kanAvslutteKlage(utfyltVilkårsvurdertKlageTilVurdering().second)
    }

    @Test
    fun `kan avslutte bekreftet vilkårsvurdert klage til vurdering`() {
        kanAvslutteKlage(bekreftetVilkårsvurdertKlageTilVurdering().second)
    }

    @Test
    fun `kan avslutte utfylt avvist vilkårsvurdert klage`() {
        kanAvslutteKlage(utfyltAvvistVilkårsvurdertKlage().second)
    }

    @Test
    fun `kan avslutte bekreftet avvist vilkårsvurdert klage`() {
        kanAvslutteKlage(bekreftetAvvistVilkårsvurdertKlage().second)
    }

    @Test
    fun `kan avslutte påbegynt vurdert klage`() {
        kanAvslutteKlage(påbegyntVurdertKlage().second)
    }

    @Test
    fun `kan avslutte utfylt vurdert klage`() {
        kanAvslutteKlage(utfyltVurdertKlage().second)
    }

    @Test
    fun `kan avslutte bekreftet vurdert klage`() {
        kanAvslutteKlage(bekreftetVurdertKlage().second)
    }

    @Test
    fun `kan avslutte avvist klage`() {
        kanAvslutteKlage(avvistKlage().second)
    }

    private fun kanAvslutteKlage(
        klage: Klage,
    ) {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
        )
        val saksbehandler = NavIdentBruker.Saksbehandler("Saksbehandler som avsluttet klagen")
        val begrunnelse = "Begrunnelse for å avslutte klagen"
        mocks.service.avslutt(
            klageId = klage.id,
            saksbehandler = saksbehandler,
            begrunnelse = begrunnelse,
        ) shouldBe AvsluttetKlage(klage, saksbehandler, begrunnelse, fixedTidspunkt).right()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).defaultTransactionContext()
        verify(mocks.klageRepoMock).lagre(
            argThat {
                it shouldBe AvsluttetKlage(
                    forrigeSteg = klage,
                    saksbehandler = saksbehandler,
                    begrunnelse = begrunnelse,
                    tidspunktAvsluttet = fixedTidspunkt,
                )
            },
            anyOrNull(),
        )
        mocks.verifyNoMoreInteractions()
    }
}
