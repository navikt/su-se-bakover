package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingTilAttestering
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class LeggTilFradragTest {

    @Test
    fun `ugyldig status for å legge til fradrag`() {
        val (_, tilAttestering) = revurderingTilAttestering()

        lagFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 100.0,
            periode = tilAttestering.periode,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        ).let {
            tilAttestering.oppdaterFradrag(listOf(it)) shouldBe Revurdering.KunneIkkeLeggeTilFradrag.UgyldigTilstand(
                tilAttestering::class,
                OpprettetRevurdering::class,
            ).left()

            tilAttestering.oppdaterFradragOgMarkerSomVurdert(listOf(it)) shouldBe Revurdering.KunneIkkeLeggeTilFradrag.UgyldigTilstand(
                tilAttestering::class,
                OpprettetRevurdering::class,
            ).left()
        }
    }

    @Test
    fun `kryssvaliderer fradrag og bosituasjon`() {
        val (_, opprettetRevurdering) = opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                bosituasjongrunnlagEnslig(),
            ),
        )

        lagFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 0.0,
            periode = opprettetRevurdering.periode,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        ).let {
            opprettetRevurdering.oppdaterFradrag(listOf(it)) shouldBe Revurdering.KunneIkkeLeggeTilFradrag.Valideringsfeil(
                KunneIkkeLageGrunnlagsdata.Konsistenssjekk(Konsistensproblem.BosituasjonOgFradrag.KombinasjonAvBosituasjonOgFradragErUgyldig),
            ).left()

            opprettetRevurdering.oppdaterFradragOgMarkerSomVurdert(listOf(it)) shouldBe Revurdering.KunneIkkeLeggeTilFradrag.Valideringsfeil(
                KunneIkkeLageGrunnlagsdata.Konsistenssjekk(Konsistensproblem.BosituasjonOgFradrag.KombinasjonAvBosituasjonOgFradragErUgyldig),
            ).left()
        }
    }

    @Test
    fun `tryner hvis perioden for alle fradrag ikke er innenfor revurderingens periode`() {
        val (_, opprettetRevurdering) = opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                bosituasjongrunnlagEnslig(),
            ),
        )

        listOf(
            lagFradragsgrunnlag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 0.0,
                periode = opprettetRevurdering.periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            ),
            lagFradragsgrunnlag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 0.0,
                periode = opprettetRevurdering.periode.forskyv(1),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            ),
        ).let {
            assertThrows<IllegalArgumentException> {
                opprettetRevurdering.oppdaterFradrag(it)
            }
            assertThrows<IllegalArgumentException> {
                opprettetRevurdering.oppdaterFradragOgMarkerSomVurdert(it)
            }
        }
    }
}
