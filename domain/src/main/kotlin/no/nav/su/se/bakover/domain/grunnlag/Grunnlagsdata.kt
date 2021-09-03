package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
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
    fun oppdaterGrunnlagsperioder(
        oppdatertPeriode: Periode,
    ): Grunnlagsdata {
        return tryCreate(
            fradragsgrunnlag = fradragsgrunnlag.oppdaterFradragsperiode(oppdatertPeriode),
            bosituasjon = bosituasjon.oppdaterBosituasjonsperiode(oppdatertPeriode),
        ).getOrHandle { throw IllegalStateException("Kunne ikke oppdatere stønadsperiode for grunnlagsdata") }
    }

    val periode: Periode? by lazy {
        fradragsgrunnlag.map { it.fradrag.periode }.plus(bosituasjon.map { it.periode }).ifNotEmpty {
            Periode.create(
                fraOgMed = this.minOf { it.fraOgMed },
                tilOgMed = this.maxOf { it.tilOgMed },
            )
        }
    }

    companion object {
        val IkkeVurdert = Grunnlagsdata(fradragsgrunnlag = emptyList(), bosituasjon = emptyList())

        /** Denne skal ikke kalles på produksjon på sikt */
        fun create(
            fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag> = emptyList(),
            bosituasjon: List<Grunnlag.Bosituasjon> = emptyList(),
        ) = tryCreate(fradragsgrunnlag, bosituasjon).getOrHandle { throw IllegalStateException(it.toString()) }

        fun tryCreate(
            fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
            bosituasjon: List<Grunnlag.Bosituasjon>,
        ): Either<KunneIkkeLageGrunnlagsdata, Grunnlagsdata> {

            val fradragsperiode = fradragsgrunnlag.periode()
            val bosituasjonperiode = bosituasjon.periode()

            if (fradragsperiode != null) {
                if (bosituasjonperiode == null) return KunneIkkeLageGrunnlagsdata.MåLeggeTilBosituasjonFørFradrag.left()
                if (!(bosituasjonperiode inneholder fradragsperiode))
                    return KunneIkkeLageGrunnlagsdata.FradragManglerBosituasjon.left()

                fradragsgrunnlag.map { f ->
                    if (f.fradrag.tilhører == FradragTilhører.EPS) {
                        f.fradrag.periode.tilMånedsperioder().forEach { fradragMnd ->
                            if (bosituasjon.filter { it.harEktefelle() }.map {
                                it.periode
                            }.none {
                                    it inneholder fradragMnd
                                }
                            ) return KunneIkkeLageGrunnlagsdata.FradragForEpsSomIkkeHarEPS.left()
                        }
                    }
                }
            }

            return Grunnlagsdata(
                fradragsgrunnlag = fradragsgrunnlag,
                bosituasjon = bosituasjon,
            ).right()
        }
    }
}

sealed class KunneIkkeLageGrunnlagsdata {
    object MåLeggeTilBosituasjonFørFradrag : KunneIkkeLageGrunnlagsdata()
    object FradragManglerBosituasjon : KunneIkkeLageGrunnlagsdata()
    object FradragForEpsSomIkkeHarEPS : KunneIkkeLageGrunnlagsdata()
}

fun List<Grunnlag.Uføregrunnlag>.harForventetInntektStørreEnn0() = this.sumOf { it.forventetInntekt } > 0
fun List<Grunnlag.Fradragsgrunnlag>.harEpsInntekt() = this.any { it.fradrag.tilhørerEps() }
fun List<Grunnlag.Fradragsgrunnlag>.periode(): Periode? = this.map { it.fradrag.periode }.let { perioder ->
    if (perioder.isEmpty()) null else
        Periode.create(
            fraOgMed = perioder.minOf { it.fraOgMed },
            tilOgMed = perioder.maxOf { it.tilOgMed },
        )
}

fun List<Grunnlag.Fradragsgrunnlag>.oppdaterFradragsperiode(
    oppdatertPeriode: Periode,
): List<Grunnlag.Fradragsgrunnlag> {
    return this.map { it.oppdaterFradragsperiode(oppdatertPeriode) }
}

fun List<Grunnlag.Bosituasjon>.oppdaterBosituasjonsperiode(oppdatertPeriode: Periode): List<Grunnlag.Bosituasjon> {
    return this.map { it.oppdaterBosituasjonsperiode(oppdatertPeriode) }
}

@JvmName("bosituasjonperiode")
fun List<Grunnlag.Bosituasjon>.periode(): Periode? = this.map { it.periode }.let { perioder ->
    if (perioder.isEmpty()) null else
        Periode.create(
            fraOgMed = perioder.minOf { it.fraOgMed },
            tilOgMed = perioder.maxOf { it.tilOgMed },
        )
}
