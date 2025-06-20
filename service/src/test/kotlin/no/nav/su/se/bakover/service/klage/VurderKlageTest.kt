package no.nav.su.se.bakover.service.klage

import arrow.core.left
import behandling.klage.domain.Hjemmel
import behandling.klage.domain.KlageId
import behandling.klage.domain.VurderingerTilKlage
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
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
            omgjør = null,
            oppretthold = null,
        )
        mocks.service.vurderOmOmgjøringEllerOpprettholdelse(request) shouldBe KunneIkkeVurdereKlage.FantIkkeKlage.left()

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
        )
        mocks.service.vurderOmOmgjøringEllerOpprettholdelse(request) shouldBe KunneIkkeVurdereKlage.UgyldigOmgjøringsårsak.left()

        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `ugyldig omgjøringsutfall`() {
        val mocks = KlageServiceMocks()
        val request = KlageVurderingerRequest(
            klageId = KlageId.generer(),
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            fritekstTilBrev = null,
            omgjør = KlageVurderingerRequest.Omgjør(null, "UGYLDIG_OMGJØRINGSUTFALL"),
            oppretthold = null,
        )
        mocks.service.vurderOmOmgjøringEllerOpprettholdelse(request) shouldBe KunneIkkeVurdereKlage.UgyldigOmgjøringsutfall.left()
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
            oppretthold = KlageVurderingerRequest.Oppretthold(listOf("UGYLDIG_HJEMMEL")),
        )
        mocks.service.vurderOmOmgjøringEllerOpprettholdelse(request) shouldBe KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler.left()
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
                utfall = null,
            ),
            oppretthold = KlageVurderingerRequest.Oppretthold(
                hjemler = listOf(),
            ),
        )
        mocks.service.vurderOmOmgjøringEllerOpprettholdelse(request) shouldBe KunneIkkeVurdereKlage.KanIkkeVelgeBådeOmgjørOgOppretthold.left()
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
        val request = KlageVurderingerRequest(
            klageId = klage.id,
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            fritekstTilBrev = null,
            omgjør = null,
            oppretthold = null,
        )
        mocks.service.vurderOmOmgjøringEllerOpprettholdelse(request) shouldBe KunneIkkeVurdereKlage.UgyldigTilstand(
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
        )

        mocks.service.vurderOmOmgjøringEllerOpprettholdelse(request).getOrFail().also {
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
            oppretthold = KlageVurderingerRequest.Oppretthold(listOf("SU_PARAGRAF_3")),
        )
        mocks.service.vurderOmOmgjøringEllerOpprettholdelse(request).getOrFail().also {
            it.saksbehandler shouldBe NavIdentBruker.Saksbehandler("nySaksbehandler")
            it.vurderinger shouldBe VurderingerTilKlage.Utfylt(
                fritekstTilOversendelsesbrev = "fritekstTilBrev",
                vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(listOf(Hjemmel.SU_PARAGRAF_3))
                    .getOrFail() as VurderingerTilKlage.Vedtaksvurdering.Utfylt,
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
