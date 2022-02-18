package no.nav.su.se.bakover.service.klage

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.Klageinstanshendelser
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.avvistKlage
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.fixedTidspunkt
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

internal class BekreftVurdertKlageTest {

    @Test
    fun `fant ikke klage`() {

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            }
        )

        val klageId = UUID.randomUUID()
        val saksbehandler = NavIdentBruker.Saksbehandler("s2")
        mocks.service.bekreftVurderinger(
            klageId = klageId,
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeBekrefteKlagesteg.FantIkkeKlage.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `en bekreftet vurdert klage er en åpen klage`() {
        val klage = bekreftetVurdertKlage().second
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
    fun `Ugyldig tilstandsovergang fra utfylt avvist`() {
        verifiserUgyldigTilstandsovergang(
            klage = utfyltAvvistVilkårsvurdertKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vilkårsvurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = bekreftetVilkårsvurdertKlageTilVurdering().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt avvist`() {
        verifiserUgyldigTilstandsovergang(
            klage = avvistKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet avvist`() {
        verifiserUgyldigTilstandsovergang(
            klage = bekreftetAvvistVilkårsvurdertKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = påbegyntVurdertKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra til attestering`() {
        verifiserUgyldigTilstandsovergang(
            klage = vurdertKlageTilAttestering().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra oversendt`() {
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
        mocks.service.bekreftVurderinger(
            klageId = klage.id,
            saksbehandler = klage.saksbehandler,
        ) shouldBe KunneIkkeBekrefteKlagesteg.UgyldigTilstand(klage::class, VurdertKlage.Bekreftet::class).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne bekrefte utfylt vurdert klage`() {
        utfyltVurdertKlage().let {
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
        bekreftetVurdertKlage().let {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                vilkårsvurderingerTilKlage = it.second.vilkårsvurderinger,
                vurderingerTilKlage = it.second.vurderinger,
            )
        }
    }

    @Test
    fun `Skal kunne bekrefte underkjent klage`() {
        underkjentKlageTilVurdering().let {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                vilkårsvurderingerTilKlage = it.second.vilkårsvurderinger,
                vurderingerTilKlage = it.second.vurderinger,
                attesteringer = it.second.attesteringer,
            )
        }
    }

    private fun verifiserGyldigStatusovergang(
        vedtak: Vedtak,
        klage: Klage,
        vilkårsvurderingerTilKlage: VilkårsvurderingerTilKlage.Utfylt,
        vurderingerTilKlage: VurderingerTilKlage.Utfylt,
        attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()
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

        var expectedKlage: VurdertKlage.Bekreftet?
        mocks.service.bekreftVurderinger(
            klageId = klage.id,
            saksbehandler = NavIdentBruker.Saksbehandler("bekreftetVilkårsvurderingene"),
        ).orNull()!!.also {
            expectedKlage = VurdertKlage.Bekreftet.create(
                id = it.id,
                opprettet = fixedTidspunkt,
                sakId = klage.sakId,
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = NavIdentBruker.Saksbehandler("bekreftetVilkårsvurderingene"),
                vilkårsvurderinger = vilkårsvurderingerTilKlage,
                vurderinger = vurderingerTilKlage,
                attesteringer = attesteringer,
                datoKlageMottatt = 1.desember(2021),
                klageinstanshendelser = Klageinstanshendelser.empty()
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
            }
        )
        mocks.verifyNoMoreInteractions()
    }
}
