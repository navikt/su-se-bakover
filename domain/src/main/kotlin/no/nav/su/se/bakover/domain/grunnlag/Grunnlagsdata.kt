package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.inneholder
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.oppdaterBosituasjonsperiode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.oppdaterFradragsperiode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Uføregrunnlag
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.time.Clock

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
    /**
     * Det er vanskelig å si noe her om den er ferdig utfylt eller ikke, da det er akseptabelt med ingen fradragsgrunnlag.
     * Men dersom vi har minst en bosituasjon, betyr det at den lovlig kan iverksettes.
     */
    val erUtfylt: Boolean = bosituasjon.isNotEmpty()

    init {
        val bosituasjonsperiode = bosituasjon.map { it.periode }
        bosituasjonsperiode.zipWithNext { a, b ->
            require(!a.overlapper(b)) {
                "Bosituasjonsperioder i grunnlagsdata overlapper. Perioder: $bosituasjon"
            }
        }
        val fradragsperiode = fradragsgrunnlag.map { it.periode }

        if (fradragsperiode.isNotEmpty() && bosituasjonsperiode.isNotEmpty()) {
            require(bosituasjonsperiode inneholder fradragsperiode) {
                "Bosituasjonsperiodene: $bosituasjonsperiode må inneholde fradragsperiodene: $fradragsperiode."
            }
        }
    }

    // TODO("flere_satser det gir egentlig ikke mening at vi oppdaterer flere verdier på denne måten, bør sees på/vurderes fjernet")
    fun oppdaterGrunnlagsperioder(
        oppdatertPeriode: Periode,
        clock: Clock,
    ): Either<KunneIkkeLageGrunnlagsdata, Grunnlagsdata> {
        return tryCreateTillatUfullstendigBosituasjon(
            fradragsgrunnlag = fradragsgrunnlag.oppdaterFradragsperiode(oppdatertPeriode, clock)
                .getOrElse { return KunneIkkeLageGrunnlagsdata.UgyldigFradragsgrunnlag(it).left() },
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
        ) = tryCreate(fradragsgrunnlag, bosituasjon).getOrElse { throw IllegalStateException(it.toString()) }

        fun createTillatUfullstendigBosituasjon(
            fradragsgrunnlag: List<Fradragsgrunnlag> = emptyList(),
            bosituasjon: List<Bosituasjon> = emptyList(),
        ) = tryCreateTillatUfullstendigBosituasjon(
            fradragsgrunnlag,
            bosituasjon,
        ).getOrElse { throw IllegalStateException(it.toString()) }

        fun tryCreate(
            fradragsgrunnlag: List<Fradragsgrunnlag>,
            bosituasjon: List<Bosituasjon>,
        ): Either<KunneIkkeLageGrunnlagsdata, Grunnlagsdata> {
            return SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                bosituasjon = bosituasjon,
                fradrag = fradragsgrunnlag,
            ).resultat.mapLeft {
                KunneIkkeLageGrunnlagsdata.Konsistenssjekk(it.first())
            }.map {
                Grunnlagsdata(
                    fradragsgrunnlag = fradragsgrunnlag.sortedBy { it.periode },
                    bosituasjon = bosituasjon.sortedBy { it.periode },
                )
            }
        }

        /**
         * Tillater at vi oppretter med ufullstendig bosituasjon for å støtte søknadsbehandlinger i tidlige faser,
         * og/eller avslag før sats er tatt stilling til.
         */
        fun tryCreateTillatUfullstendigBosituasjon(
            fradragsgrunnlag: List<Fradragsgrunnlag>,
            bosituasjon: List<Bosituasjon>,
        ): Either<KunneIkkeLageGrunnlagsdata, Grunnlagsdata> {
            return SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                bosituasjon = bosituasjon,
                fradrag = fradragsgrunnlag,
            ).resultat.mapLeft { problemer ->
                when (val feil = problemer.first()) {
                    is Konsistensproblem.BosituasjonOgFradrag.UgyldigBosituasjon -> {
                        if (feil.feil.filterNot { it is Konsistensproblem.Bosituasjon.Ufullstendig }.isNotEmpty()) {
                            KunneIkkeLageGrunnlagsdata.Konsistenssjekk(feil)
                        } else {
                            return Grunnlagsdata(
                                fradragsgrunnlag = fradragsgrunnlag.sortedBy { it.periode },
                                bosituasjon = bosituasjon.sortedBy { it.periode },
                            ).right()
                        }
                    }

                    else -> {
                        KunneIkkeLageGrunnlagsdata.Konsistenssjekk(feil)
                    }
                }
            }.map {
                Grunnlagsdata(
                    fradragsgrunnlag = fradragsgrunnlag.sortedBy { it.periode },
                    bosituasjon = bosituasjon.sortedBy { it.periode },
                )
            }
        }
    }
}

sealed class KunneIkkeLageGrunnlagsdata {
    object MåLeggeTilBosituasjonFørFradrag : KunneIkkeLageGrunnlagsdata()
    object FradragManglerBosituasjon : KunneIkkeLageGrunnlagsdata()
    object FradragForEPSMenBosituasjonUtenEPS : KunneIkkeLageGrunnlagsdata()
    data class UgyldigFradragsgrunnlag(val feil: Fradragsgrunnlag.UgyldigFradragsgrunnlag) :
        KunneIkkeLageGrunnlagsdata()

    data class Konsistenssjekk(val feil: Konsistensproblem.BosituasjonOgFradrag) : KunneIkkeLageGrunnlagsdata()
}

fun List<Uføregrunnlag>.harForventetInntektStørreEnn0() = this.sumOf { it.forventetInntekt } > 0

fun List<Fradragsgrunnlag>.fjernFradragForEPSHvisEnslig(bosituasjon: Bosituasjon): List<Fradragsgrunnlag> {
    return if (bosituasjon.harEPS()) this else fjernFradragEPS(listOf(bosituasjon.periode))
}

fun List<Fradragsgrunnlag>.fjernFradragEPS(perioderUtenEPS: List<Periode>): List<Fradragsgrunnlag> {
    return flatMap { it.fjernFradragEPS(perioderUtenEPS) }
}

fun List<Bosituasjon.Fullstendig>.lagTidslinje(periode: Periode): List<Bosituasjon.Fullstendig> {
    return this.lagTidslinje()?.krympTilPeriode(periode) ?: emptyList()
}
