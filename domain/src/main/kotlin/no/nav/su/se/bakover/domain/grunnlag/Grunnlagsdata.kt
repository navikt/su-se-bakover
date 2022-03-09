package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.inneholderAlle
import no.nav.su.se.bakover.common.periode.minusListe
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.oppdaterBosituasjonsperiode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.perioder
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.oppdaterFradragsperiode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.perioder
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Uføregrunnlag
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

// TODO: Del inn i tom og utleda grunnlagsdata. F.eks. ved å bruke NonEmptyList
/**
 * Grunnlagene til vilkårene finnes under Vilkårsvurderinger
 */
data class Grunnlagsdata private constructor(
    val fradragsgrunnlag: List<Fradragsgrunnlag>,

    /**
     * Under vilkårsvurdering/opprettet: Kan være null/tom/en/fler. (fler kun ved revurdering)
     * Etter vilkårsvurdering: Skal være en. Senere kan den være fler hvis vi støtter sats per måned.
     * */
    val bosituasjon: List<Bosituasjon>,
) {
    fun oppdaterGrunnlagsperioder(
        oppdatertPeriode: Periode,
    ): Either<KunneIkkeLageGrunnlagsdata, Grunnlagsdata> {
        return tryCreate(
            fradragsgrunnlag = fradragsgrunnlag.oppdaterFradragsperiode(oppdatertPeriode)
                .getOrHandle { return KunneIkkeLageGrunnlagsdata.UgyldigFradragsgrunnlag(it).left() },
            bosituasjon = bosituasjon.oppdaterBosituasjonsperiode(oppdatertPeriode),
        )
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
            fradragsgrunnlag: List<Fradragsgrunnlag> = emptyList(),
            bosituasjon: List<Bosituasjon> = emptyList(),
        ) = tryCreate(fradragsgrunnlag, bosituasjon).getOrHandle { throw IllegalStateException(it.toString()) }

        fun tryCreate(
            fradragsgrunnlag: List<Fradragsgrunnlag>,
            bosituasjon: List<Bosituasjon>,
        ): Either<KunneIkkeLageGrunnlagsdata, Grunnlagsdata> {

            val fradragsperiode = fradragsgrunnlag.perioder()
            val bosituasjonperiode = bosituasjon.perioder()

            if (fradragsperiode.isNotEmpty()) {
                if (bosituasjonperiode.isEmpty()) {
                    return KunneIkkeLageGrunnlagsdata.MåLeggeTilBosituasjonFørFradrag.left()
                }

                if (fradragsperiode.minusListe(bosituasjonperiode).isNotEmpty()) {
                    return KunneIkkeLageGrunnlagsdata.FradragManglerBosituasjon.left()
                }

                val perioderMedEPS = bosituasjonperiode.minusListe(bosituasjon.filter { !it.harEPS() }.perioder())
                val perioderMedFradragForEPS =
                    fradragsperiode.minusListe(fradragsgrunnlag.filter { !it.tilhørerEps() }.perioder())

                if (!perioderMedEPS.inneholderAlle(perioderMedFradragForEPS)) {
                    return KunneIkkeLageGrunnlagsdata.FradragForEPSMenBosituasjonUtenEPS.left()
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
    object FradragForEPSMenBosituasjonUtenEPS : KunneIkkeLageGrunnlagsdata()
    data class UgyldigFradragsgrunnlag(val feil: Fradragsgrunnlag.UgyldigFradragsgrunnlag) :
        KunneIkkeLageGrunnlagsdata()
}

fun List<Uføregrunnlag>.harForventetInntektStørreEnn0() = this.sumOf { it.forventetInntekt } > 0

fun List<Fradragsgrunnlag>.fjernFradragForEPSHvisEnslig(bosituasjon: Bosituasjon): List<Fradragsgrunnlag> {
    return if (bosituasjon.harEPS()) this else fjernFradragEPS()
}

fun List<Fradragsgrunnlag>.fjernFradragEPS(): List<Fradragsgrunnlag> {
    return filterNot { it.tilhørerEps() }
}
