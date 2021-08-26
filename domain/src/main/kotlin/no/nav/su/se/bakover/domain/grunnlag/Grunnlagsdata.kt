package no.nav.su.se.bakover.domain.grunnlag

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

// TODO: Del inn i tom og utleda grunnlagsdata. F.eks. ved å bruke NonEmptyList
/**
 * Grunnlagene til vilkårene finnes under Vilkårsvurderinger
 */
data class Grunnlagsdata private constructor(
    val fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,

    /**
     * Under vilkårsvurdering/opprettet: Kan være null/tom/en/fler. (fler kun ved revurdering)
     * Etter vilkårsvurdering: Skal være en. Senere kan den være fler hvis vi støtter sats per måned.
     * */
    val bosituasjon: List<Grunnlag.Bosituasjon>,
) {
    val periode: Periode? by lazy {
        fradragsgrunnlag.map { it.fradrag.periode }.plus(bosituasjon.map { it.periode }).ifNotEmpty {
            Periode.create(
                fraOgMed = this.minOf { it.fraOgMed },
                tilOgMed = this.maxOf { it.tilOgMed },
            )
        }
    }

    companion object {
        val IkkeVurdert = tryCreate()

        fun tryCreate(
            fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag> = emptyList(),
            bosituasjon: List<Grunnlag.Bosituasjon> = emptyList(),
        ): Grunnlagsdata {

            val fradragsperiode = fradragsgrunnlag.periode()
            val bosituasjonperiode = bosituasjon.periode()

            if (fradragsperiode != null) {
                if (bosituasjonperiode == null) throw IllegalArgumentException("Må legge til bosituasjon før fradrag.")
                if (!(bosituasjonperiode inneholder fradragsperiode))
                    throw IllegalArgumentException("fradragslisten inneholder fradrag som ikke har noen bosituasjon")

                fradragsgrunnlag.map { f ->
                    if (f.fradrag.tilhører == FradragTilhører.EPS) {
                        f.fradrag.periode.tilMånedsperioder().forEach { fradragMnd ->
                            if (bosituasjon.filter { it.harEktefelle() }.map {
                                it.periode
                            }.none {
                                    it inneholder fradragMnd
                                }
                            ) throw IllegalArgumentException("fradragslisten inneholder fradrag for eps som ikke har eps på dette tidspunkt")
                        }
                    }
                }
            }

            return Grunnlagsdata(
                fradragsgrunnlag = fradragsgrunnlag,
                bosituasjon = bosituasjon,
            )
        }
    }
}

fun List<Grunnlag.Uføregrunnlag>.harForventetInntektStørreEnn0() = this.sumOf { it.forventetInntekt } > 0
fun List<Grunnlag.Fradragsgrunnlag>.harEpsInntekt() = this.any { it.fradrag.tilhørerEps() }
fun List<Grunnlag.Fradragsgrunnlag>.periode(): Periode? = this.map { it.fradrag.periode }.let { perioder ->
    if (perioder.isEmpty()) null else
        Periode.create(
            fraOgMed = perioder.minOf { it.fraOgMed },
            tilOgMed = perioder.maxOf { it.tilOgMed }
        )
}

@JvmName("bosituasjonperiode")
fun List<Grunnlag.Bosituasjon>.periode(): Periode? = this.map { it.periode }.let { perioder ->
    if (perioder.isEmpty()) null else
        Periode.create(
            fraOgMed = perioder.minOf { it.fraOgMed },
            tilOgMed = perioder.maxOf { it.tilOgMed }
        )
}
