package no.nav.su.se.bakover.service.klage

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.Klageinstanshendelser
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.avvistKlage
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.createBekreftetVilkårsvurdertKlage
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.påbegyntVilkårsvurdertKlage
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.underkjentKlageTilVurdering
import no.nav.su.se.bakover.test.utfyltAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.utfyltVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.utfyltVurdertKlage
import no.nav.su.se.bakover.test.vurdertKlageTilAttestering
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class BekreftVilkårsvurdertKlageTest {

    @Test
    fun `fant ikke klage`() {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )
        val klageId = UUID.randomUUID()
        val saksbehandler = NavIdentBruker.Saksbehandler("s2")
        mocks.service.bekreftVilkårsvurderinger(
            klageId = klageId,
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeBekrefteKlagesteg.FantIkkeKlage.left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `en bekreftet vilkårsvurdert(tilVurdering) er en åpen klage`() {
        val klage = bekreftetVilkårsvurdertKlageTilVurdering().second
        klage.erÅpen() shouldBe true
    }

    @Test
    fun `en bekreftet vilkårsvurdert(avvist) er en åpen klage`() {
        val klage = bekreftetAvvistVilkårsvurdertKlage().second
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
    fun `Ugyldig tilstandsovergang fra iverksattAvvist`() {
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
        mocks.service.bekreftVilkårsvurderinger(
            klageId = klage.id,
            saksbehandler = klage.saksbehandler,
        ) shouldBe KunneIkkeBekrefteKlagesteg.UgyldigTilstand(klage::class, VilkårsvurdertKlage.Bekreftet::class).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne bekrefte utfylt vilkårsvurdering(tilVurdering)`() {
        utfyltVilkårsvurdertKlageTilVurdering().also {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                vilkårsvurderingerTilKlage = it.second.vilkårsvurderinger,
                vurderingerTilKlage = it.second.vurderinger,
            )
        }
    }

    @Test
    fun `Skal kunne bekrefte utfylt vilkårsvurdering(avvist)`() {
        utfyltAvvistVilkårsvurdertKlage().also {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                vilkårsvurderingerTilKlage = it.second.vilkårsvurderinger,
                vurderingerTilKlage = null,
            )
        }
    }

    @Test
    fun `Skal kunne bekrefte bekreftet vilkårsvurdering`() {
        bekreftetVilkårsvurdertKlageTilVurdering().also {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                vilkårsvurderingerTilKlage = it.second.vilkårsvurderinger,
                vurderingerTilKlage = it.second.vurderinger,
            )
        }
    }

    @Test
    fun `Skal kunne bekrefte påbegynt vurdert klage`() {
        påbegyntVurdertKlage().also {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                vilkårsvurderingerTilKlage = it.second.vilkårsvurderinger,
                vurderingerTilKlage = it.second.vurderinger,
            )
        }
    }

    @Test
    fun `Skal kunne bekrefte utfylt vurdert klage`() {
        utfyltVurdertKlage().also {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                vilkårsvurderingerTilKlage = it.second.vilkårsvurderinger,
                vurderingerTilKlage = it.second.vurderinger,
            )
        }
    }

    @Test
    fun `Skal kunne bekrefte bekreftet vurdert klage`() {
        bekreftetVurdertKlage().also {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                vilkårsvurderingerTilKlage = it.second.vilkårsvurderinger,
                vurderingerTilKlage = it.second.vurderinger,
            )
        }
    }

    @Test
    fun `Skal kunne bekrefte påbegyntAvvist`() {
        avvistKlage().also {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                vilkårsvurderingerTilKlage = it.second.vilkårsvurderinger,
                vurderingerTilKlage = null,
            )
        }
    }

    @Test
    fun `Skal kunne bekrefte underkjent klage`() {
        underkjentKlageTilVurdering().also {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                vilkårsvurderingerTilKlage = it.second.vilkårsvurderinger,
                vurderingerTilKlage = it.second.vurderinger,
                attesteringer = it.second.attesteringer,
            )
        }
    }

    @Test
    fun `får tilbake en bekreftet avvist vilkårsvurdert klage dersom minst et av feltene er besvart 'nei' eller false`() {
        val forventetAvvistVilkårsvurdertKlage = utfyltAvvistVilkårsvurdertKlage().second.bekreftVilkårsvurderinger(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerensen"),
        ).getOrFail()

        forventetAvvistVilkårsvurdertKlage.shouldBeTypeOf<VilkårsvurdertKlage.Bekreftet.Avvist>()
    }

    private fun verifiserGyldigStatusovergang(
        vedtak: Vedtak,
        klage: Klage,
        vilkårsvurderingerTilKlage: VilkårsvurderingerTilKlage.Utfylt,
        vurderingerTilKlage: VurderingerTilKlage? = null,
        attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty(),
    ) {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            vedtakServiceMock = mock {
                on { hentForVedtakId(any()) } doReturn vedtak
            },
        )

        var expectedKlage: VilkårsvurdertKlage.Bekreftet?
        mocks.service.bekreftVilkårsvurderinger(
            klageId = klage.id,
            saksbehandler = NavIdentBruker.Saksbehandler("bekreftetVilkårsvurderingene"),
        ).getOrFail().also {
            expectedKlage = createBekreftetVilkårsvurdertKlage(
                id = it.id,
                opprettet = it.opprettet,
                sakId = klage.sakId,
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = NavIdentBruker.Saksbehandler("bekreftetVilkårsvurderingene"),
                vilkårsvurderinger = vilkårsvurderingerTilKlage,
                vurderinger = vurderingerTilKlage,
                attesteringer = attesteringer,
                datoKlageMottatt = 15.januar(2021),
                klageinstanshendelser = Klageinstanshendelser.empty(),
                fritekstTilBrev = klage.getFritekstTilBrev().getOrNull(),
            )
            it shouldBe expectedKlage
        }

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).defaultTransactionContext()
        verify(mocks.klageRepoMock).lagre(
            argThat {
                it shouldBe expectedKlage
            },
            argThat {
                it shouldBe TestSessionFactory.transactionContext
            },
        )
        mocks.verifyNoMoreInteractions()
    }
}
