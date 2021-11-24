package no.nav.su.se.bakover.service.klage

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurdertKlage
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class VilkårsvurderKlageTest {

    @Test
    fun `fant ikke vedtak`() {
        val klageRepoMock = mock<KlageRepo>()

        val sakRepoMock: SakRepo = mock()

        val vedtakRepoMock: VedtakRepo = mock {
            on { hentForVedtakId(any()) } doReturn null
        }
        val brevServiceMock: BrevService = mock()

        val klageService = KlageServiceImpl(
            sakRepo = sakRepoMock,
            klageRepo = klageRepoMock,
            vedtakRepo = vedtakRepoMock,
            brevService = brevServiceMock,
            personService = mock(),
            microsoftGraphApiClient = mock(),
            clock = fixedClock,
        )

        val vedtakId = UUID.randomUUID()
        val request = VurderKlagevilkårRequest(
            navIdent = "nySaksbehandler",
            klageId = UUID.randomUUID().toString(),
            vedtakId = vedtakId.toString(),
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )
        klageService.vilkårsvurder(request) shouldBe KunneIkkeVilkårsvurdereKlage.FantIkkeVedtak.left()

        verify(vedtakRepoMock).hentForVedtakId(vedtakId)
        verifyNoMoreInteractions(sakRepoMock, klageRepoMock, vedtakRepoMock)
    }

    @Test
    fun `fant ikke klage`() {
        val klageRepoMock = mock<KlageRepo> {
            on { hentKlage(any()) } doReturn null
        }
        val sakRepoMock: SakRepo = mock()
        val vedtakRepoMock: VedtakRepo = mock()
        val brevServiceMock: BrevService = mock()
        val klageService = KlageServiceImpl(
            sakRepo = sakRepoMock,
            klageRepo = klageRepoMock,
            vedtakRepo = vedtakRepoMock,
            brevService = brevServiceMock,
            personService = mock(),
            microsoftGraphApiClient = mock(),
            clock = fixedClock,
        )

        val klageId = UUID.randomUUID()
        val request = VurderKlagevilkårRequest(
            navIdent = "s2",
            klageId = klageId.toString(),
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )
        klageService.vilkårsvurder(request) shouldBe KunneIkkeVilkårsvurdereKlage.FantIkkeKlage.left()

        verify(klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        verifyNoMoreInteractions(sakRepoMock, klageRepoMock, vedtakRepoMock)
    }

    @Test
    fun `kan påbegynne vilkårsvurdering`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val opprettetKlage = opprettetKlage(
            sakId = sak.id,
        )
        val klageRepoMock = mock<KlageRepo> {
            on { hentKlage(any()) } doReturn opprettetKlage
        }
        val vedtakRepoMock: VedtakRepo = mock {
            on { hentForVedtakId(any()) } doReturn vedtak
        }
        val sakRepoMock: SakRepo = mock()
        val brevServiceMock: BrevService = mock()

        val klageService = KlageServiceImpl(
            sakRepo = sakRepoMock,
            klageRepo = klageRepoMock,
            vedtakRepo = vedtakRepoMock,
            brevService = brevServiceMock,
            personService = mock(),
            microsoftGraphApiClient = mock(),
            clock = fixedClock,
        )

        val request = VurderKlagevilkårRequest(
            navIdent = "nySaksbehandler",
            klageId = opprettetKlage.id.toString(),
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )

        var expectedKlage: VilkårsvurdertKlage.Påbegynt?
        klageService.vilkårsvurder(request).orNull()!!.also {
            expectedKlage = VilkårsvurdertKlage.Påbegynt.create(
                id = it.id,
                opprettet = fixedTidspunkt,
                sakId = sak.id,
                journalpostId = opprettetKlage.journalpostId,
                saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.empty(),
            )
            it shouldBe expectedKlage
        }

        verify(klageRepoMock).hentKlage(argThat { it shouldBe opprettetKlage.id })
        verify(klageRepoMock).lagre(
            argThat {
                it shouldBe expectedKlage
            },
        )
        verifyNoMoreInteractions(sakRepoMock, klageRepoMock, vedtakRepoMock)
    }

    @Test
    fun `kan ferdigstille vilkårsvurdering`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val påbegyntVilkårsvurdertKlage = vilkårsvurdertKlage(
            sakId = sak.id,
        )
        val klageRepoMock = mock<KlageRepo> {
            on { hentKlage(any()) } doReturn påbegyntVilkårsvurdertKlage
        }
        val vedtakRepoMock: VedtakRepo = mock {
            on { hentForVedtakId(any()) } doReturn vedtak
        }
        val sakRepoMock: SakRepo = mock()
        val brevServiceMock: BrevService = mock()

        val klageService = KlageServiceImpl(
            sakRepo = sakRepoMock,
            klageRepo = klageRepoMock,
            vedtakRepo = vedtakRepoMock,
            brevService = brevServiceMock,
            personService = mock(),
            microsoftGraphApiClient = mock(),
            clock = fixedClock,
        )

        val request = VurderKlagevilkårRequest(
            navIdent = "nySaksbehandler",
            klageId = påbegyntVilkårsvurdertKlage.id.toString(),
            vedtakId = vedtak.id.toString(),
            innenforFristen = true,
            klagesDetPåKonkreteElementerIVedtaket = true,
            erUnderskrevet = true,
            begrunnelse = "SomeBegrunnelse",
        )

        var expectedKlage: VilkårsvurdertKlage.Utfylt?
        klageService.vilkårsvurder(request).orNull()!!.also {
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
            )
            it shouldBe expectedKlage
        }

        verify(vedtakRepoMock).hentForVedtakId(vedtak.id)
        verify(klageRepoMock).hentKlage(argThat { it shouldBe påbegyntVilkårsvurdertKlage.id })
        verify(klageRepoMock).lagre(
            argThat {
                it shouldBe expectedKlage
            },
        )
        verifyNoMoreInteractions(sakRepoMock, klageRepoMock, vedtakRepoMock)
    }
}
