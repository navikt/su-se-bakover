package no.nav.su.se.bakover.service.klage

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurdertKlage
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class VilkårsvurderKlageTest {

    @Test
    fun `fant ikke vedtak`() {

        val mocks = KlageServiceMocks(
            vedtakRepoMock = mock {
                on { hentForVedtakId(any()) } doReturn null
            },
        )
        val vedtakId = UUID.randomUUID()
        val request = VurderKlagevilkårRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = UUID.randomUUID(),
            vedtakId = vedtakId,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )
        mocks.service.vilkårsvurder(request) shouldBe KunneIkkeVilkårsvurdereKlage.FantIkkeVedtak.left()

        verify(mocks.vedtakRepoMock).hentForVedtakId(vedtakId)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke klage`() {

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )
        val klageId = UUID.randomUUID()
        val request = VurderKlagevilkårRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            klageId = klageId,
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )
        mocks.service.vilkårsvurder(request) shouldBe KunneIkkeVilkårsvurdereKlage.FantIkkeKlage.left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan påbegynne vilkårsvurdering`() {

        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()

        val opprettetKlage = opprettetKlage(
            sakId = sak.id,
        )

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn opprettetKlage
            },
            vedtakRepoMock = mock {
                on { hentForVedtakId(any()) } doReturn vedtak
            },
        )

        val request = VurderKlagevilkårRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = opprettetKlage.id,
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )

        var expectedKlage: VilkårsvurdertKlage.Påbegynt?
        mocks.service.vilkårsvurder(request).orNull()!!.also {
            expectedKlage = VilkårsvurdertKlage.Påbegynt.create(
                id = it.id,
                opprettet = fixedTidspunkt,
                sakId = sak.id,
                journalpostId = opprettetKlage.journalpostId,
                saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.empty(),
                vurderinger = null,
                attesteringer = Attesteringshistorikk.empty(),
            )
            it shouldBe expectedKlage
        }

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe opprettetKlage.id })
        verify(mocks.klageRepoMock).lagre(
            argThat {
                it shouldBe expectedKlage
            },
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan ferdigstille vilkårsvurdering`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val påbegyntVilkårsvurdertKlage = vilkårsvurdertKlage(
            sakId = sak.id,
        )
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn påbegyntVilkårsvurdertKlage
            },
            vedtakRepoMock = mock {
                on { hentForVedtakId(any()) } doReturn vedtak
            },
        )
        val request = VurderKlagevilkårRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = påbegyntVilkårsvurdertKlage.id,
            vedtakId = vedtak.id,
            innenforFristen = true,
            klagesDetPåKonkreteElementerIVedtaket = true,
            erUnderskrevet = true,
            begrunnelse = "SomeBegrunnelse",
        )
        var expectedKlage: VilkårsvurdertKlage.Utfylt?
        mocks.service.vilkårsvurder(request).orNull()!!.also {
            expectedKlage = VilkårsvurdertKlage.Utfylt.create(
                id = it.id,
                opprettet = fixedTidspunkt,
                sakId = sak.id,
                journalpostId = påbegyntVilkårsvurdertKlage.journalpostId,
                saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.Utfylt(
                    vedtakId = it.vilkårsvurderinger.vedtakId!!,
                    innenforFristen = true,
                    klagesDetPåKonkreteElementerIVedtaket = true,
                    erUnderskrevet = true,
                    begrunnelse = "SomeBegrunnelse",
                ),
                vurderinger = null,
                attesteringer = Attesteringshistorikk.empty(),
            )
            it shouldBe expectedKlage
        }

        verify(mocks.vedtakRepoMock).hentForVedtakId(vedtak.id)
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe påbegyntVilkårsvurdertKlage.id })
        verify(mocks.klageRepoMock).lagre(
            argThat {
                it shouldBe expectedKlage
            },
        )
        mocks.verifyNoMoreInteractions()
    }
}
