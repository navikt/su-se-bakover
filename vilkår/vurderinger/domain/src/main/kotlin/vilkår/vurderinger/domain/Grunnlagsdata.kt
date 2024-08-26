package vilkår.vurderinger.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tid.periode.NonEmptySlåttSammenIkkeOverlappendePerioder
import no.nav.su.se.bakover.common.domain.tid.periode.SlåttSammenIkkeOverlappendePerioder
import no.nav.su.se.bakover.common.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.erSortertPåFraOgMed
import no.nav.su.se.bakover.common.tid.periode.erSortertPåFraOgMedDeretterTilOgMed
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import no.nav.su.se.bakover.common.tid.periode.inneholder
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.bosituasjon.domain.grunnlag.epsForMåned
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag.Companion.oppdaterStønadsperiode
import vilkår.uføre.domain.Uføregrunnlag
import java.time.Clock

// TODO: Del inn i tom og utleda grunnlagsdata. F.eks. ved å bruke NonEmptyList
/**
 * Grunnlagene til vilkårene finnes under Vilkårsvurderinger
 *
 * @param fradragsgrunnlag Kan inneholde overlappende perioder; også innenfor en type. Den er sortert på fraOgMed, deretter tilOgMed. Merk at du ikke kan ha fradrag utenfor bosituasjonsperioden.
 * @param bosituasjon Inneholder ikke overlappende perioder. Er sortert. Under søknadsbehandling vil den være tom fram til bosituasjonen er lagt til. Deretter en eller fler.
 */
data class Grunnlagsdata private constructor(
    val fradragsgrunnlag: List<Fradragsgrunnlag>,
    val bosituasjon: List<Bosituasjon>,
) {
    /**
     * Distincte fnr for eps i bosituasjonen.
     */
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
        require(!bosituasjonsperiode.harOverlappende()) {
            "Bosituasjonsperioder i grunnlagsdata overlapper. Perioder: $bosituasjon"
        }
        require(bosituasjonsperiode.erSortertPåFraOgMed()) {
            "Bosituasjonsperioder i grunnlagsdata er ikke sortert på fraOgMed. Perioder: $bosituasjon"
        }
        val fradragsperiode = fradragsgrunnlag.map { it.periode }
        // Vi kan ha fradragsperioder som overlapper hverandre, også per type.
        require(fradragsperiode.erSortertPåFraOgMedDeretterTilOgMed()) {
            "Fradragsperioder i grunnlagsdata er ikke sortert på fraOgMed og tilOgMed. Perioder: $fradragsgrunnlag"
        }

        if (fradragsperiode.isNotEmpty() && bosituasjonsperiode.isNotEmpty()) {
            require(bosituasjonsperiode inneholder fradragsperiode) {
                "Bosituasjonsperiodene: $bosituasjonsperiode må inneholde fradragsperiodene: $fradragsperiode."
            }
        }
    }

    /**
     * Kaster dersom ikke alle bosituasjonene er fullstendig.
     */
    fun oppdaterStønadsperiode(
        nyPeriode: Stønadsperiode,
        clock: Clock,
    ): Either<KunneIkkeLageGrunnlagsdata, Grunnlagsdata> {
        return tryCreate(
            fradragsgrunnlag = fradragsgrunnlag.oppdaterStønadsperiode(nyPeriode, clock)
                .getOrElse { return KunneIkkeLageGrunnlagsdata.UgyldigFradragsgrunnlag(it).left() },
            bosituasjon = when {
                bosituasjon.isEmpty() -> emptyList()
                bosituasjon.size == 1 -> listOf(bosituasjon.single().oppdaterStønadsperiode(nyPeriode))
                // TODO jah: Dersom vi legger inn støtte for flere bosituasjoner under søknadsbehandling, så må dette kravet sees over.
                else -> throw IllegalStateException("Denne er kun beregnet på Søknadsbehandling med 1 bosituasjon, siden oppdaterBosituasjonsperiode vil sette alle bosituasjonene til samme periode.")
            },
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
            return BosituasjonOgFradrag(
                bosituasjon = bosituasjon,
                fradrag = fradragsgrunnlag,
            ).resultat.mapLeft {
                KunneIkkeLageGrunnlagsdata.Konsistenssjekk(it.first())
            }.map {
                Grunnlagsdata(
                    fradragsgrunnlag = fradragsgrunnlag
                        .sortedWith(compareBy<Fradragsgrunnlag> { it.periode.fraOgMed }.thenBy { it.periode.tilOgMed }),
                    // Init sjekker at disse ikke har overlapp
                    bosituasjon = bosituasjon.sortedBy { it.periode.fraOgMed },
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
            return BosituasjonOgFradrag(
                bosituasjon = bosituasjon,
                fradrag = fradragsgrunnlag,
            ).resultat.mapLeft { problemer ->
                when (val feil = problemer.first()) {
                    is Konsistensproblem.BosituasjonOgFradrag.UgyldigBosituasjon -> {
                        if (feil.feil.filterNot { it is Konsistensproblem.Bosituasjon.Ufullstendig }.isNotEmpty()) {
                            KunneIkkeLageGrunnlagsdata.Konsistenssjekk(feil)
                        } else {
                            return Grunnlagsdata(
                                fradragsgrunnlag = fradragsgrunnlag
                                    .sortedWith(compareBy<Fradragsgrunnlag> { it.periode.fraOgMed }.thenBy { it.periode.tilOgMed }),
                                // Init sjekker at disse ikke har overlapp
                                bosituasjon = bosituasjon.sortedBy { it.periode.fraOgMed },
                            ).right()
                        }
                    }

                    else -> {
                        KunneIkkeLageGrunnlagsdata.Konsistenssjekk(feil)
                    }
                }
            }.map {
                Grunnlagsdata(
                    fradragsgrunnlag = fradragsgrunnlag
                        .sortedWith(compareBy<Fradragsgrunnlag> { it.periode.fraOgMed }.thenBy { it.periode.tilOgMed }),
                    // Init sjekker at disse ikke har overlapp
                    bosituasjon = bosituasjon.sortedBy { it.periode.fraOgMed },
                )
            }
        }
    }

    fun copyWithNewIds(): Grunnlagsdata = Grunnlagsdata(
        fradragsgrunnlag = fradragsgrunnlag.map { it.copyWithNewId() },
        bosituasjon = bosituasjon.map { it.copyWithNewId() as Bosituasjon.Fullstendig },
    )

    /**
     * Gir et Map fra måned til fødselsnummer for eps innenfor dette grunnlaget.
     */
    fun epsForMåned(): Map<Måned, Fnr> = this.bosituasjon.epsForMåned()
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
    return if (bosituasjon.harEPS()) this else fjernFradragEPS(NonEmptySlåttSammenIkkeOverlappendePerioder.create(bosituasjon.periode))
}
fun List<Fradragsgrunnlag>.fjernFradragEPS(perioderUtenEPS: SlåttSammenIkkeOverlappendePerioder): List<Fradragsgrunnlag> {
    return flatMap { it.fjernFradragEPS(perioderUtenEPS) }
}

fun List<Bosituasjon.Fullstendig>.lagTidslinje(periode: Periode): List<Bosituasjon.Fullstendig> {
    return this.lagTidslinje()?.krympTilPeriode(periode) ?: emptyList()
}
