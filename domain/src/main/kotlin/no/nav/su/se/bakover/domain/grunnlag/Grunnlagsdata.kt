package no.nav.su.se.bakover.domain.grunnlag

import no.nav.su.se.bakover.common.periode.Periode
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

// TODO: Del inn i tom og utleda grunnlagsdata. F.eks. ved å bruke NonEmptyList
/**
 * Grunnlagene til vilkårene finnes under Vilkårsvurderinger
 */
data class Grunnlagsdata(
    val fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag> = emptyList(),
    /**
     * Under vilkårsvurdering/opprettet: Kan være null/tom/en/fler. (fler kun ved revurdering)
     * Etter vilkårsvurdering: Skal være en. Senere kan den være fler hvis vi støtter sats per måned.
     * */
    val bosituasjon: List<Grunnlag.Bosituasjon> = emptyList(),
) {
    // TODO jah: Valider at de vurderte uføre-periodene er de samme som de vurderte formue-periodene
    companion object {
        val IkkeVurdert = Grunnlagsdata()
    }

    val periode: Periode? by lazy {
        fradragsgrunnlag.map { it.fradrag.periode }.plus(bosituasjon.map { it.periode }).ifNotEmpty {
            Periode.create(
                fraOgMed = this.minOf { it.fraOgMed },
                tilOgMed = this.maxOf { it.tilOgMed },
            )
        }
    }
}

fun List<Grunnlag.Uføregrunnlag>.harForventetInntektStørreEnn0() = this.sumOf { it.forventetInntekt } > 0
fun List<Grunnlag.Fradragsgrunnlag>.harEpsInntekt() = this.any { it.fradrag.tilhørerEps() }
