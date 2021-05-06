package no.nav.su.se.bakover.service.vilkår

import arrow.core.Nel
import arrow.core.left
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import org.junit.jupiter.api.Test
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
                on { toVurderingsperiode(any()) } doReturn testArg.first.left()
            }
            LeggTilUførevurderingerRequest(
                behandlingId = behandlingId,
                vurderinger = Nel.of(leggTilUførevurderingRequest),
            ).toVilkår(
                Periode.create(1.januar(2021), 31.januar(2021)),
            ) shouldBe testArg.second.left()
        }
    }

    @Test
    fun `Kan ikke ha overlappende vurderingsperioder`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = Nel.of(
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
        ) shouldBe LeggTilUførevurderingerRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder.left()
    }

    @Test
    fun `Kan ikke ha blande VilkårOppfylt og VilkårIkkeOppfylt`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = Nel.of(
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
        ) shouldBe LeggTilUførevurderingerRequest.UgyldigUførevurdering.AlleVurderingeneMåHaSammeResultat.left()
    }

    @Test
    fun `Kan ikke ha blande VilkårOppfylt og HarUføresakTilBehandling`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = Nel.of(
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
        ) shouldBe LeggTilUførevurderingerRequest.UgyldigUførevurdering.AlleVurderingeneMåHaSammeResultat.left()
    }

    @Test
    fun `Kan ikke ha blande VilkårIkkeOppfylt og HarUføresakTilBehandling`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = Nel.of(
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
        ) shouldBe LeggTilUførevurderingerRequest.UgyldigUførevurdering.AlleVurderingeneMåHaSammeResultat.left()
    }

    @Test
    fun `Støtter fler VilkårOppfylt`() {
        val behandlingId = UUID.randomUUID()
        val actual = LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = Nel.of(
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
        ).orNull()!!
        actual shouldBe Vilkår.Vurdert.Uførhet.create(
            vurderingsperioder = Nel.of(
                Vurderingsperiode.Manuell.create(
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
                Vurderingsperiode.Manuell.create(
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
            vurderinger = Nel.of(
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
                    oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
                    begrunnelse = null,
                ),
            ),
        ).toVilkår(
            Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 28.februar(2021),
            ),
        ).orNull()!!
        actual shouldBe Vilkår.Vurdert.Uførhet.create(
            vurderingsperioder = Nel.of(
                Vurderingsperiode.Manuell.create(
                    id = actual.vurderingsperioder[0].id,
                    opprettet = actual.vurderingsperioder[0].opprettet,
                    resultat = Resultat.Avslag,
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
                Vurderingsperiode.Manuell.create(
                    id = actual.vurderingsperioder[1].id,
                    opprettet = actual.vurderingsperioder[1].opprettet,
                    resultat = Resultat.Avslag,
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
}
