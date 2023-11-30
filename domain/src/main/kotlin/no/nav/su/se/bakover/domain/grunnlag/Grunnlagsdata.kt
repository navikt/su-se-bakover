package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.inneholder
import no.nav.su.se.bakover.domain.grunnlag.Fradragsgrunnlag.Companion.oppdaterFradragsperiode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.oppdaterBosituasjonsperiode
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
    val eps: List<Fnr> = bosituasjon.mapNotNull { it.eps }.distinct().sortedBy { it.toString() }

    /**
     * Det er vanskelig å si noe her om den er ferdig utfylt eller ikke, da det er akseptabelt med ingen fradragsgrunnlag.
     * Men dersom vi har minst en bosituasjon, betyr det at den lovlig kan iverksettes.
     */
    val erUtfylt: Boolean = bosituasjon.isNotEmpty()

    fun bosituasjonSomFullstendig(): List<Bosituasjon.Fullstendig> {
        return bosituasjon.map { it as Bosituasjon.Fullstendig }
    }

    fun kastHvisIkkeAlleBosituasjonerErFullstendig() {
        check(bosituasjon.all { it is Bosituasjon.Fullstendig })
    }

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

    /**
     * Kaster dersom ikke alle bosituasjonene er fullstendig.
     */
    // TODO("flere_satser det gir egentlig ikke mening at vi oppdaterer flere verdier på denne måten, bør sees på/vurderes fjernet")
    fun oppdaterGrunnlagsperioder(
        oppdatertPeriode: Periode,
        clock: Clock,
    ): Either<KunneIkkeLageGrunnlagsdata, Grunnlagsdata> {
        require(bosituasjon.size <= 1) {
            "Denne er kun beregnet på Søknadsbehandling med 1 bosituasjon, siden oppdaterBosituasjonsperiode vil sette alle bosituasjonene til samme periode."
        }
        return tryCreate(
            fradragsgrunnlag = fradragsgrunnlag.oppdaterFradragsperiode(oppdatertPeriode, clock)
                .getOrElse { return KunneIkkeLageGrunnlagsdata.UgyldigFradragsgrunnlag(it).left() },
            bosituasjon = bosituasjonSomFullstendig().oppdaterBosituasjonsperiode(oppdatertPeriode),
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
        fun create(
            fradragsgrunnlag: List<Fradragsgrunnlag> = emptyList(),
            bosituasjon: List<Bosituasjon.Fullstendig> = emptyList(),
        ) = tryCreate(
            fradragsgrunnlag,
            bosituasjon,
        ).getOrElse { throw IllegalStateException(it.toString()) }

        fun tryCreate(
            fradragsgrunnlag: List<Fradragsgrunnlag>,
            bosituasjon: List<Bosituasjon.Fullstendig>,
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
         * Skal kun kalles av persisteringslaget og expect i tester.
         */
        fun createTillatUfullstendigBosituasjon(
            fradragsgrunnlag: List<Fradragsgrunnlag> = emptyList(),
            bosituasjon: List<Bosituasjon> = emptyList(),
        ) = tryCreateTillatUfullstendigBosituasjon(
            fradragsgrunnlag,
            bosituasjon,
        ).getOrElse { throw IllegalStateException(it.toString()) }

        /**
         * Skal kun kalles av persisteringslaget og expect i tester.
         *
         * Tillater at vi oppretter med ufullstendig bosituasjon for å støtte søknadsbehandlinger i tidlige faser,
         * og/eller avslag før sats er tatt stilling til.
         */
        private fun tryCreateTillatUfullstendigBosituasjon(
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

sealed interface KunneIkkeLageGrunnlagsdata {
    data object MåLeggeTilBosituasjonFørFradrag : KunneIkkeLageGrunnlagsdata
    data object FradragManglerBosituasjon : KunneIkkeLageGrunnlagsdata
    data object FradragForEPSMenBosituasjonUtenEPS : KunneIkkeLageGrunnlagsdata
    data class UgyldigFradragsgrunnlag(val feil: Fradragsgrunnlag.UgyldigFradragsgrunnlag) : KunneIkkeLageGrunnlagsdata

    data class Konsistenssjekk(val feil: Konsistensproblem.BosituasjonOgFradrag) : KunneIkkeLageGrunnlagsdata
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
