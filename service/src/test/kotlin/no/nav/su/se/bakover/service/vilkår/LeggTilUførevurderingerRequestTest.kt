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
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUføre
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class LeggTilUførevurderingerRequestTest {

    @Test
    fun `mapper LeggTilUførevurderingRequest til riktig feiltype`() {
        listOf(
            LeggTilUførevilkårRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler to LeggTilUførevurderingerRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler,
            LeggTilUførevilkårRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig to LeggTilUførevurderingerRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig,
            LeggTilUførevilkårRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder to LeggTilUførevurderingerRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder,
            LeggTilUførevilkårRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden to LeggTilUførevurderingerRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden,
        ).forEach { testArg ->
            val behandlingId = UUID.randomUUID()
            val leggTilUførevilkårRequest = mock<LeggTilUførevilkårRequest> {
                on { toVurderingsperiode(any()) } doReturn testArg.first.left()
            }
            LeggTilUførevurderingerRequest(
                behandlingId = behandlingId,
                vurderinger = nonEmptyListOf(leggTilUførevilkårRequest),
            ).toVilkår(
                januar(2021), fixedClock,
            ) shouldBe testArg.second.left()
        }
    }

    @Test
    fun `Kan ikke ha overlappende vurderingsperioder`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = nonEmptyListOf(
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.juli(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.VilkårOppfylt,
                    begrunnelse = null,
                ),
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = februar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.VilkårOppfylt,
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
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mars(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.VilkårOppfylt,
                    begrunnelse = null,
                ),
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.juni(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.VilkårOppfylt,
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
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = januar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.VilkårOppfylt,
                    begrunnelse = null,
                ),
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = februar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.VilkårIkkeOppfylt,
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
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = januar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.VilkårOppfylt,
                    begrunnelse = null,
                ),
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = februar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.HarUføresakTilBehandling,
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
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = januar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.VilkårIkkeOppfylt,
                    begrunnelse = null,
                ),
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = februar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.HarUføresakTilBehandling,
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
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = januar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.VilkårOppfylt,
                    begrunnelse = null,
                ),
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = februar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.VilkårOppfylt,
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
        actual shouldBe UføreVilkår.Vurdert.create(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeUføre.create(
                    id = actual.vurderingsperioder[0].id,
                    opprettet = actual.vurderingsperioder[0].opprettet,
                    vurdering = Vurdering.Innvilget,
                    grunnlag = Grunnlag.Uføregrunnlag(
                        id = actual.vurderingsperioder[0].grunnlag!!.id,
                        opprettet = actual.vurderingsperioder[0].grunnlag!!.opprettet,
                        periode = januar(2021),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 12000,
                    ),
                    periode = januar(2021),
                ),
                VurderingsperiodeUføre.create(
                    id = actual.vurderingsperioder[1].id,
                    opprettet = actual.vurderingsperioder[1].opprettet,
                    vurdering = Vurdering.Innvilget,
                    grunnlag = Grunnlag.Uføregrunnlag(
                        id = actual.vurderingsperioder[1].grunnlag!!.id,
                        opprettet = actual.vurderingsperioder[1].grunnlag!!.opprettet,
                        periode = februar(2021),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 12000,
                    ),
                    periode = februar(2021),
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
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = januar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.VilkårOppfylt,
                    begrunnelse = null,
                ),
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = februar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.VilkårOppfylt,
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
        actual shouldBe UføreVilkår.Vurdert.create(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeUføre.create(
                    id = actual.vurderingsperioder[0].id,
                    opprettet = actual.vurderingsperioder[0].opprettet,
                    vurdering = Vurdering.Innvilget,
                    grunnlag = Grunnlag.Uføregrunnlag(
                        id = actual.vurderingsperioder[0].grunnlag!!.id,
                        opprettet = actual.vurderingsperioder[0].grunnlag!!.opprettet,
                        periode = januar(2021),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 12000,
                    ),
                    periode = januar(2021),
                ),
                VurderingsperiodeUføre.create(
                    id = actual.vurderingsperioder[1].id,
                    opprettet = actual.vurderingsperioder[1].opprettet,
                    vurdering = Vurdering.Innvilget,
                    grunnlag = Grunnlag.Uføregrunnlag(
                        id = actual.vurderingsperioder[1].grunnlag!!.id,
                        opprettet = actual.vurderingsperioder[1].grunnlag!!.opprettet,
                        periode = februar(2021),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 12000,
                    ),
                    periode = februar(2021),
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
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = januar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.VilkårIkkeOppfylt,
                    begrunnelse = "blah",
                ),
            ),
        ).toVilkår(januar(2021), fixedClock).orNull()!!
            .let { request ->
                UføreVilkår.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        VurderingsperiodeUføre.create(
                            id = request.vurderingsperioder[0].id,
                            opprettet = request.vurderingsperioder[0].opprettet,
                            vurdering = Vurdering.Innvilget,
                            grunnlag = null,
                            periode = januar(2021),
                        ),
                    ),
                )
            }

        LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = nonEmptyListOf(
                LeggTilUførevilkårRequest(
                    behandlingId = behandlingId,
                    periode = januar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12000,
                    oppfylt = UførevilkårStatus.HarUføresakTilBehandling,
                    begrunnelse = "blah",
                ),
            ),
        ).toVilkår(januar(2021), fixedClock).orNull()!!
            .let { request ->
                UføreVilkår.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        VurderingsperiodeUføre.create(
                            id = request.vurderingsperioder[0].id,
                            opprettet = request.vurderingsperioder[0].opprettet,
                            vurdering = Vurdering.Uavklart,
                            grunnlag = null,
                            periode = januar(2021),
                        ),
                    ),
                )
            }
    }
}
