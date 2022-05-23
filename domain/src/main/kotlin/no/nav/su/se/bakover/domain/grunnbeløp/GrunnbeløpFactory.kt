package no.nav.su.se.bakover.domain.grunnbeløp

import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.periode.periodisert
import no.nav.su.se.bakover.domain.satser.supplerendeStønadAlderFlyktningIkrafttredelse
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * Fra lov om supplerende stønad (https://lovdata.no/dokument/NL/lov/2005-04-29-21):
 * - Med grunnbeløpet meiner ein i lova her grunnbeløpet etter [folketrygdlova § 1-4](https://lovdata.no/dokument/NL/lov/1997-02-28-19/KAPITTEL_2-1#%C2%A71-4).
 * - Grunnbeløpet er en beregningsfaktor som har betydning for retten til ytelser og for størrelsen på ytelser etter denne loven.
 * - Grunnbeløpet fastsettes av Kongen og reguleres årlig med virkning fra 1. mai i samsvar med lønnsveksten.
 *
 * @param clock angir nåtiden for dennee instansen. Styrer hvilke grunnbeløpsendringer denne insatnsen kjenner til, basert på
 * kombinasjonen av [clock] og [Grunnbeløpsendring.ikrafttredelse]. Instansen vil altså bare "se" endringer som har trådt i kraft på
 * tidspunktet gitt av [LocalDate.now] for aktuell [clock].
 * @param grunnbeløpsendringer liste med alle kjente endringer i grunnbeløp (kan også være fremtidige som enda ikke har tredt i kraft)
 *
 */
class GrunnbeløpFactory(
    /**
     * En klokke som representerer nåtid for denne instansen. Brukes til å styre hvilke grunnbeløpsendringer denne
     * instansen skal "se".
     */
    private val clock: Clock,
    /**
     * Liste over alle kjente grunnbeløpsendringer.
     */
    private val grunnbeløpsendringer: List<Grunnbeløpsendring>,
) {
    private val månedTilGrunnbeløp = grunnbeløpsendringer.periodiserIftVirkningstidspunkt(LocalDate.now(clock))

    init {
        assert(månedTilGrunnbeløp.any { it.key.inneholder(supplerendeStønadAlderFlyktningIkrafttredelse) })
        assert(månedTilGrunnbeløp.isNotEmpty())
        månedTilGrunnbeløp.erSammenhengendeSortertOgUtenDuplikater()
    }

    fun forMåned(måned: Måned): GrunnbeløpForMåned {
        return månedTilGrunnbeløp[måned]!!
    }

    fun alleGrunnbeløp(fraOgMed: LocalDate): List<GrunnbeløpForMåned> {
        return månedTilGrunnbeløp.filterValues {
            it.måned starterSamtidigEllerSenere Måned.fra(
                YearMonth.of(
                    fraOgMed.year,
                    fraOgMed.month,
                ),
            )
        }.values.toList()
    }

    fun alle(): List<GrunnbeløpForMåned> {
        return alleGrunnbeløp(månedTilGrunnbeløp.minOf { it.key.fraOgMed })
    }

    /**
     * Konstruerer en factory med grunnbeløpene som var gjeldende på [påDato] ved å fjerne alle grunnbeløp som har
     * en [GrunnbeløpForMåned.ikrafttredelse] senere enn [påDato].
     */
    fun gjeldende(påDato: LocalDate): GrunnbeløpFactory {
        return GrunnbeløpFactory(
            clock = påDato.fixedClock(), // lager en fixed clock som representerer nåtid for påDato
            grunnbeløpsendringer = grunnbeløpsendringer,
        )
    }
}

data class Grunnbeløpsendring(
    /** angir datoen endringen skal virke fra og med etter den har trådt i kraft */
    val virkningstidspunkt: LocalDate,
    /** angir datoen endringen trer i kraft */
    val ikrafttredelse: LocalDate,
    /** grunnbeløpet */
    val verdi: Int,
)

private fun List<Grunnbeløpsendring>.periodiserIftVirkningstidspunkt(senesteIkrafttredelse: LocalDate): Map<Måned, GrunnbeløpForMåned> {
    return filterNot { it.ikrafttredelse > senesteIkrafttredelse }
        .map { it.virkningstidspunkt to it }
        .periodisert()
        .associate { (virkningstidspunkt, måned, grunnbeløpsendring) ->
            måned to GrunnbeløpForMåned(
                måned = måned,
                grunnbeløpPerÅr = grunnbeløpsendring.verdi,
                ikrafttredelse = grunnbeløpsendring.ikrafttredelse,
                virkningstidspunkt = virkningstidspunkt,
            )
        }
}
