package vilkår.formue.domain

import grunnbeløp.domain.GrunnbeløpFactory
import no.nav.su.se.bakover.common.domain.tid.endOfMonth
import no.nav.su.se.bakover.common.domain.tid.startOfMonth
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.erSammenhengendeSortertOgUtenDuplikater
import vilkår.formue.domain.FormuegrenserFactory.Companion.createFromGrunnbeløp
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Et av vilkårene for å få Supplerende Stønad er at formuen er under et halvt grunnbeløp.
 * Grunnbeløpet endres årlig og vil få virkning fra 1. mai, men trer som regel i kraft i løpet av mai eller senere.
 * [createFromGrunnbeløp] tar inn et [GrunnbeløpFactory] som baserer seg på et gitt virkningspunkt. For eksempel når applikasjonen startet, eller enda bedre; nå.
 * Så lenge vi kun legger i grunnbeløpsverdier etter de har tredd i kraft og kun vurderer vilkår på nytt, vil ikke dette bli et problem.
 * TODO jah: Men dersom vi f.eks. skal gå tilbake i tid og verifisere et vilkår på et gitt tidspunkt, må vi kunne justere for tidspunktet vedtaket ble iverksatt.
 *
 * @param tidligsteTilgjengeligeMåned Tidligste tilgjengelige måned denne satsen er aktuell. Som for denne satsen er 2021-01-01, men siden vi har tester som antar den gjelder før dette er den dynamisk.
 */
class FormuegrenserFactory private constructor(
    private val månedTilFormuegrense: Map<Måned, FormuegrenseForMåned>,
    val tidligsteTilgjengeligeMåned: Måned,
) {
    private val sisteMånedMedEndring: Måned = månedTilFormuegrense.keys.last()

    init {
        require(månedTilFormuegrense.isNotEmpty())
        require(månedTilFormuegrense.erSammenhengendeSortertOgUtenDuplikater())
        require(månedTilFormuegrense.keys.first() == tidligsteTilgjengeligeMåned)
    }

    companion object {
        fun createFromGrunnbeløp(
            grunnbeløpFactory: GrunnbeløpFactory,
            tidligsteTilgjengeligeMåned: Måned,
        ): FormuegrenserFactory {
            require(tidligsteTilgjengeligeMåned == grunnbeløpFactory.tidligsteTilgjengeligeMåned)
            return FormuegrenserFactory(
                månedTilFormuegrense = grunnbeløpFactory.alleGrunnbeløp(tidligsteTilgjengeligeMåned)
                    .associate { grunnbeløpForMåned ->
                        grunnbeløpForMåned.måned to FormuegrenseForMåned(
                            grunnbeløpForMåned = grunnbeløpForMåned,
                        )
                    },
                tidligsteTilgjengeligeMåned = tidligsteTilgjengeligeMåned,
            )
        }
    }

    /**
     * @throws ArrayIndexOutOfBoundsException dersom måneden er utenfor tidslinjen
     */
    fun forMåned(måned: Måned): FormuegrenseForMåned {
        return månedTilFormuegrense[måned]
            ?: if (måned > sisteMånedMedEndring) {
                månedTilFormuegrense[sisteMånedMedEndring]!!.let {
                    it.copy(
                        grunnbeløpForMåned = it.grunnbeløpForMåned.copy(
                            måned = måned,
                        ),
                    )
                }
            } else {
                throw IllegalArgumentException(
                    "Har ikke data for etterspurt måned: $måned. Vi har bare data fra og med måned: ${månedTilFormuegrense.keys.first()}",
                )
            }
    }

    fun forDato(dato: LocalDate): FormuegrenseForMåned {
        return forMåned(Måned.fra(dato.startOfMonth(), dato.endOfMonth()))
    }

    /**
     * En liste, sortert etter dato i synkende rekkefølge.
     * Hver dato gir tilhørende formuegrense per år.
     * E.g. (2021-05-01 to 53199.5) siden formuegrensen fom. 5. mai 2021 var en halv G. Grunnbeløpet var da 106399.
     */
    fun virkningstidspunkt(fraOgMed: Måned): List<Pair<LocalDate, BigDecimal>> {
        val tidligsteTilgjengeligeMåned = forMåned(fraOgMed)
        return månedTilFormuegrense
            .filter { it.value.virkningstidspunkt >= tidligsteTilgjengeligeMåned.virkningstidspunkt }
            .map { it.value.virkningstidspunkt to it.value.formuegrense }
            .distinct()
            .sortedByDescending { it.first }
    }

    override fun equals(other: Any?): Boolean {
        return other is FormuegrenserFactory && other.månedTilFormuegrense == this.månedTilFormuegrense
    }

    override fun hashCode(): Int {
        return månedTilFormuegrense.hashCode()
    }
}
