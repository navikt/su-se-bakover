package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Nel
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import org.junit.jupiter.api.Test
import java.util.UUID

internal class RevurderingTest {
    @Test
    fun `beregning gir feilmelding hvis vilkår er uavklart`() {
        lagRevurdering(
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.IkkeVurdert.Uførhet,
            ),
        ).beregn(emptyList()) shouldBeLeft Revurdering.KunneIkkeBeregneRevurdering.UfullstendigVilkårsvurdering
    }

    @Test
    fun `beregning gir opphør hvis vilkår ikke er oppfylt`() {
        lagRevurdering(
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Vurdert.Uførhet.create(
                    vurderingsperioder = Nel.of(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Avslag,
                            grunnlag = null,
                            periode = Periode.create(1.januar(2021), 31.desember(2021)),
                            begrunnelse = null,
                        ),
                    ),
                ),
            ),
        ).copy(informasjonSomRevurderes = InformasjonSomRevurderes(mapOf(Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert)))
            .beregn(emptyList()).orNull()!!.let {
            it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
            it.informasjonSomRevurderes[Revurderingsteg.Inntekt] shouldBe Vurderingstatus.Vurdert
        }
    }

    @Test
    fun `beregning gir ikke opphør hvis vilkår er oppfylt`() {
        lagRevurdering(
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Vurdert.Uførhet.create(
                    vurderingsperioder = Nel.of(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = Periode.create(1.januar(2021), 31.desember(2021)),
                            begrunnelse = null,
                        ),
                    ),
                ),
            ),
        ).copy(informasjonSomRevurderes = InformasjonSomRevurderes(mapOf(Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert)))
            .beregn(emptyList()).orNull()!!.let {
            it shouldNotBe beOfType<BeregnetRevurdering.Opphørt>()
            it.informasjonSomRevurderes[Revurderingsteg.Inntekt] shouldBe Vurderingstatus.Vurdert
        }
    }

    private fun lagRevurdering(
        tilRevurdering: Vedtak.EndringIYtelse = tilRevurderingMock,
        vilkårsvurderinger: Vilkårsvurderinger,
    ) = OpprettetRevurdering(
        id = UUID.randomUUID(),
        periode = Periode.create(1.januar(2021), 31.desember(2021)),
        opprettet = Tidspunkt.now(),
        tilRevurdering = tilRevurdering,
        saksbehandler = NavIdentBruker.Saksbehandler(navIdent = ""),
        oppgaveId = OppgaveId(value = ""),
        fritekstTilBrev = "",
        revurderingsårsak = Revurderingsårsak(
            årsak = Revurderingsårsak.Årsak.INFORMASJON_FRA_KONTROLLSAMTALE,
            begrunnelse = Revurderingsårsak.Begrunnelse.create(value = "b"),
        ),
        forhåndsvarsel = null,
        behandlingsinformasjon = mock() {
            on { getBeregningStrategy() } doReturn BeregningStrategy.BorAlene.right()
        },
        grunnlagsdata = Grunnlagsdata(uføregrunnlag = listOf()),
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = InformasjonSomRevurderes(emptyMap()),
    )

    private val tilRevurderingMock: Vedtak.EndringIYtelse = mock() {
        on { beregning } doReturn BeregningFactory.ny(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = Periode.create(1.januar(2021), 31.desember(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            begrunnelse = null,
        )
    }
}
