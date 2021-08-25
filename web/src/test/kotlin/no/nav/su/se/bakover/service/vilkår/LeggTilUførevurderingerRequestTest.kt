package no.nav.su.se.bakover.service.vilkår

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.web.fixedClock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class LeggTilUførevurderingerRequestTest {

    @Test
    fun `mapper LeggTilUførevurderingRequest til riktig feiltype`() {
        listOf(
            LeggTilUførevurderingRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler to LeggTilUførevurderingerRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler,
            LeggTilUførevurderingRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig to LeggTilUførevurderingerRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig,
            LeggTilUførevurderingRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder to LeggTilUførevurderingerRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder,
            LeggTilUførevurderingRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden to LeggTilUførevurderingerRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden,
        ).forEach { testArg ->
            val behandlingId = UUID.randomUUID()
            val leggTilUførevurderingRequest = mock<LeggTilUførevurderingRequest> {
                on { toVurderingsperiode(any(), any()) } doReturn testArg.first.left()
            }
            LeggTilUførevurderingerRequest(
                behandlingId = behandlingId,
                vurderinger = nonEmptyListOf(leggTilUførevurderingRequest),
            ).toVilkår(
                Periode.create(1.januar(2021), 31.januar(2021)), fixedClock,
            ) shouldBe testArg.second.left()
        }
    }

    @Test
    fun `Kan ikke ha overlappende vurderingsperioder`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = nonEmptyListOf(
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.juli(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    begrunnelse = null,
                ),
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    begrunnelse = null,
                ),
            ),
        ).toVilkår(
            Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.juli(2021),
            ),
            fixedClock,
        ) shouldBe LeggTilUførevurderingerRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder.left()
    }

    @Test
    fun `Hele behandlingsperioden må ha vurderinger`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = nonEmptyListOf(
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mars(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    begrunnelse = null,
                ),
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.juni(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    begrunnelse = null,
                ),
            ),
        ).toVilkår(
            Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.juli(2021),
            ),
            fixedClock,
        ) shouldBe LeggTilUførevurderingerRequest.UgyldigUførevurdering.HeleBehandlingsperiodenMåHaVurderinger.left()
    }

    @Test
    fun `Kan ikke ha blande VilkårOppfylt og VilkårIkkeOppfylt`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = nonEmptyListOf(
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    begrunnelse = null,
                ),
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
                    begrunnelse = null,
                ),
            ),
        ).toVilkår(
            Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 28.februar(2021),
            ),
            fixedClock,
        ) shouldBe LeggTilUførevurderingerRequest.UgyldigUførevurdering.AlleVurderingeneMåHaSammeResultat.left()
    }

    @Test
    fun `Kan ikke ha blande VilkårOppfylt og HarUføresakTilBehandling`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = nonEmptyListOf(
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    begrunnelse = null,
                ),
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling,
                    begrunnelse = null,
                ),
            ),
        ).toVilkår(
            Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 28.februar(2021),
            ),
            fixedClock,
        ) shouldBe LeggTilUførevurderingerRequest.UgyldigUførevurdering.AlleVurderingeneMåHaSammeResultat.left()
    }

    @Test
    fun `Kan ikke ha blande VilkårIkkeOppfylt og HarUføresakTilBehandling`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = nonEmptyListOf(
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
                    begrunnelse = null,
                ),
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling,
                    begrunnelse = null,
                ),
            ),
        ).toVilkår(
            Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 28.februar(2021),
            ),
            fixedClock,
        ) shouldBe LeggTilUførevurderingerRequest.UgyldigUførevurdering.AlleVurderingeneMåHaSammeResultat.left()
    }

    @Test
    fun `Støtter fler VilkårOppfylt`() {
        val behandlingId = UUID.randomUUID()
        val actual = LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = nonEmptyListOf(
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    begrunnelse = null,
                ),
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    begrunnelse = null,
                ),
            ),
        ).toVilkår(
            Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 28.februar(2021),
            ),
            fixedClock,
        ).orNull()!!
        actual shouldBe Vilkår.Uførhet.Vurdert.create(
            vurderingsperioder = nonEmptyListOf(
                Vurderingsperiode.Uføre.create(
                    id = actual.vurderingsperioder[0].id,
                    opprettet = actual.vurderingsperioder[0].opprettet,
                    resultat = Resultat.Innvilget,
                    grunnlag = Grunnlag.Uføregrunnlag(
                        id = actual.vurderingsperioder[0].grunnlag!!.id,
                        opprettet = actual.vurderingsperioder[0].grunnlag!!.opprettet,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 12000,
                    ),
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    begrunnelse = null,
                ),
                Vurderingsperiode.Uføre.create(
                    id = actual.vurderingsperioder[1].id,
                    opprettet = actual.vurderingsperioder[1].opprettet,
                    resultat = Resultat.Innvilget,
                    grunnlag = Grunnlag.Uføregrunnlag(
                        id = actual.vurderingsperioder[1].grunnlag!!.id,
                        opprettet = actual.vurderingsperioder[1].grunnlag!!.opprettet,
                        periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 12000,
                    ),
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    begrunnelse = null,
                ),
            ),
        )
    }

    @Test
    fun `Støtter fler VilkårIkkeOppfylt`() {
        val behandlingId = UUID.randomUUID()
        val actual = LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = nonEmptyListOf(
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    begrunnelse = null,
                ),
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    begrunnelse = null,
                ),
            ),
        ).toVilkår(
            Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 28.februar(2021),
            ),
            fixedClock,
        ).orNull()!!
        actual shouldBe Vilkår.Uførhet.Vurdert.create(
            vurderingsperioder = nonEmptyListOf(
                Vurderingsperiode.Uføre.create(
                    id = actual.vurderingsperioder[0].id,
                    opprettet = actual.vurderingsperioder[0].opprettet,
                    resultat = Resultat.Innvilget,
                    grunnlag = Grunnlag.Uføregrunnlag(
                        id = actual.vurderingsperioder[0].grunnlag!!.id,
                        opprettet = actual.vurderingsperioder[0].grunnlag!!.opprettet,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 12000,
                    ),
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    begrunnelse = null,
                ),
                Vurderingsperiode.Uføre.create(
                    id = actual.vurderingsperioder[1].id,
                    opprettet = actual.vurderingsperioder[1].opprettet,
                    resultat = Resultat.Innvilget,
                    grunnlag = Grunnlag.Uføregrunnlag(
                        id = actual.vurderingsperioder[1].grunnlag!!.id,
                        opprettet = actual.vurderingsperioder[1].grunnlag!!.opprettet,
                        periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 12000,
                    ),
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    begrunnelse = null,
                ),
            ),
        )
    }

    @Test
    fun `setter grunnlag til null dersom vilkår ikke er oppfylt`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = nonEmptyListOf(
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
                    begrunnelse = "blah",
                ),
            ),
        ).toVilkår(Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)), fixedClock).orNull()!!
            .let { request ->
                Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = request.vurderingsperioder[0].id,
                            opprettet = request.vurderingsperioder[0].opprettet,
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                            begrunnelse = "blah",
                        ),
                    ),
                )
            }

        LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = nonEmptyListOf(
                LeggTilUførevurderingRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling,
                    begrunnelse = "blah",
                ),
            ),
        ).toVilkår(Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)), fixedClock).orNull()!!
            .let { request ->
                Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = request.vurderingsperioder[0].id,
                            opprettet = request.vurderingsperioder[0].opprettet,
                            resultat = Resultat.Uavklart,
                            grunnlag = null,
                            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                            begrunnelse = "blah",
                        ),
                    ),
                )
            }
    }
}
