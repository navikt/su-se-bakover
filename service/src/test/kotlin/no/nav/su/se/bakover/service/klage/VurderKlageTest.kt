package no.nav.su.se.bakover.service.klage

import arrow.core.left
import behandling.klage.domain.Hjemmel
import behandling.klage.domain.KlageId
import behandling.klage.domain.Klagehjemler
import behandling.klage.domain.VurderingerTilKlage
import behandling.klage.domain.VurderingerTilKlage.Vedtaksvurdering.Årsak
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.service.klage.KlageVurderingerRequest.SkalTilKabal
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
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

internal class VurderKlageTest {

    @Test
    fun `fant ikke klage`() {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )
        val klageId = KlageId.generer()
        val request = KlageVurderingerRequest(
            klageId = klageId,
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            fritekstTilBrev = null,
            omgjør = KlageVurderingerRequest.Omgjør(Årsak.FEIL_LOVANVENDELSE.name, "begrunnelse"),
            oppretthold = null,
            delvisomgjøring_ka = null,
        )
        mocks.service.vurder(request) shouldBe KunneIkkeVurdereKlage.FantIkkeKlage.left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `ugyldig omgjøringsårsak`() {
        val mocks = KlageServiceMocks()
        val request = KlageVurderingerRequest(
            klageId = KlageId.generer(),
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            fritekstTilBrev = null,
            omgjør = KlageVurderingerRequest.Omgjør("UGYLDIG_OMGJØRINGSÅRSAK", null),
            oppretthold = null,
            delvisomgjøring_ka = null,
        )
        mocks.service.vurder(request) shouldBe KunneIkkeVurdereKlage.UgyldigOmgjøringsårsak.left()

        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `ugyldig opprettholdelseshjemler`() {
        val mocks = KlageServiceMocks()
        val request = KlageVurderingerRequest(
            klageId = KlageId.generer(),
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            fritekstTilBrev = null,
            omgjør = null,
            oppretthold = SkalTilKabal(listOf("UGYLDIG_HJEMMEL"), klagenotat = null, erOppretthold = true),
            delvisomgjøring_ka = null,
        )
        mocks.service.vurder(request) shouldBe KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler.left()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan ikke velge både omgjør og oppretthold`() {
        val mocks = KlageServiceMocks()
        val request = KlageVurderingerRequest(
            klageId = KlageId.generer(),
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            fritekstTilBrev = null,
            omgjør = KlageVurderingerRequest.Omgjør(
                årsak = null,
                begrunnelse = null,
            ),
            oppretthold = SkalTilKabal(
                hjemler = listOf(),
                klagenotat = null,
                erOppretthold = true,
            ),
            delvisomgjøring_ka = null,
        )
        mocks.service.vurder(request) shouldBe KunneIkkeVurdereKlage.ForMangeUtfall.left()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `en påbegyntVurdert klage er en åpen klage`() {
        val klage = påbegyntVurdertKlage().second
        klage.erÅpen() shouldBe true
    }

    @Test
    fun `en utfyltVurdert klage er en åpen klage`() {
        val klage = utfyltVurdertKlage().second
        klage.erÅpen() shouldBe true
    }

    @Test
    fun `Ugyldig tilstandsovergang fra opprettet`() {
        verifiserUgyldigTilstandsovergang(
            klage = opprettetKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vilkårsvurdert`() {
        verifiserUgyldigTilstandsovergang(
            klage = påbegyntVilkårsvurdertKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vilkårsvurdert`() {
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
    fun `Ugyldig tilstandsovergang fra bekreftet avvist`() {
        verifiserUgyldigTilstandsovergang(
            klage = bekreftetAvvistVilkårsvurdertKlage().second,
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
        val hjemler = Klagehjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).getOrFail().toList()
        val request = KlageVurderingerRequest(
            klageId = klage.id,
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            fritekstTilBrev = null,
            omgjør = null,
            oppretthold = SkalTilKabal(hjemler.map { it.name }, klagenotat = "klagenotat", erOppretthold = true),
            delvisomgjøring_ka = null,
        )
        mocks.service.vurder(request) shouldBe KunneIkkeVurdereKlage.UgyldigTilstand(
            klage::class,
        ).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne vurdere bekreftet vilkårsvurdert klage`() {
        val klage = bekreftetVilkårsvurdertKlageTilVurdering().second
        verifiserGyldigStatusovergangTilPåbegynt(
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vurdere påbegynt vurdert klage`() {
        val klage = påbegyntVurdertKlage().second
        verifiserGyldigStatusovergangTilPåbegynt(
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vurdere utfylt vurdert klage`() {
        val klage = utfyltVurdertKlage().second
        verifiserGyldigStatusovergangTilPåbegynt(
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vurdere bekreftet vurdert klage`() {
        val klage = bekreftetVurdertKlage().second
        verifiserGyldigStatusovergangTilPåbegynt(
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vurdere underkjent klage`() {
        val klage = underkjentKlageTilVurdering().second
        verifiserGyldigStatusovergangTilPåbegynt(
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            klage = klage,
        )
    }

    private fun verifiserGyldigStatusovergangTilPåbegynt(
        klage: Klage,
    ) {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
        )
        val request = KlageVurderingerRequest(
            klageId = klage.id,
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            fritekstTilBrev = null,
            omgjør = null,
            oppretthold = null,
            delvisomgjøring_ka = null,
        )

        mocks.service.vurder(request).getOrFail().also {
            it.saksbehandler shouldBe NavIdentBruker.Saksbehandler("nySaksbehandler")
            it.vurderinger shouldBe VurderingerTilKlage.empty()
        }

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).defaultTransactionContext()
        verify(mocks.klageRepoMock).lagre(
            any(),
            argThat {
                it shouldBe TestSessionFactory.transactionContext
            },
        )
        mocks.verifyNoMoreInteractions()
    }

    private fun verifiserGyldigStatusovergangTilUtfylt(
        klage: Klage,
    ) {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
        )
        val request = KlageVurderingerRequest(
            klageId = klage.id,
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            fritekstTilBrev = "fritekstTilBrev",
            omgjør = null,
            oppretthold = SkalTilKabal(listOf("SU_PARAGRAF_3"), klagenotat = "klagenotat", erOppretthold = true),
            delvisomgjøring_ka = null,
        )
        mocks.service.vurder(request).getOrFail().also {
            it.saksbehandler shouldBe NavIdentBruker.Saksbehandler("nySaksbehandler")
            it.vurderinger shouldBe VurderingerTilKlage.UtfyltOppretthold(
                fritekstTilOversendelsesbrev = "fritekstTilBrev",
                vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createDelvisEllerOpprettholdelse(listOf(Hjemmel.SU_PARAGRAF_3), klagenotat = "klagenotat", erOppretthold = true)
                    .getOrFail() as VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold,
            )
        }

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).defaultTransactionContext()
        verify(mocks.klageRepoMock).lagre(
            any(),
            argThat {
                it shouldBe TestSessionFactory.transactionContext
            },
        )
        mocks.verifyNoMoreInteractions()
    }
}
