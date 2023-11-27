package sats

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.extensions.erFørsteDagIMåned
import no.nav.su.se.bakover.common.extensions.erSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.erSammenhengende
import no.nav.su.se.bakover.common.tid.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.tid.periode.erSortert
import no.nav.su.se.bakover.common.tid.periode.harDuplikater
import java.time.LocalDate

/**
 * Gjør om en liste med key-value par av ([LocalDate] - [T]) til en avgrenset, sammenhengende liste hvor verdien [T]
 * hvert element i [this] ekstrapoleres fra [LocalDate] til [LocalDate] for neste element i listen.
 *
 * Eksempelbruk er satser (f.eks. grunnbeløp, garantipensjon o.l.)
 *
 * Garanterer at tidslinjen er sammenhengende, sortert og ikke har duplikater.
 * @throws IllegalArgumentException dersom listen inneholder duplikate eller usorterte datoer; eller en dato ikke er den første i måneden.
 * @param tidligsteTilgjengeligeMåned filtrerer vekk alle månedene før dette.
 */
fun <T> RåSatser<T>.periodisert(tidligsteTilgjengeligeMåned: Måned): Månedssatser<T> {
    check(this.any { it.måned <= tidligsteTilgjengeligeMåned }) {
        "Kan ikke periodisere siden vi mangler data for første ønsket måned: $tidligsteTilgjengeligeMåned. Tidligste måned tilgjengelig er ${this.first().måned}"
    }
    if (this.size == 1 || this.all { it.måned < tidligsteTilgjengeligeMåned }) {
        // 1. Dersom vi kun har et element, returner det. Bump måned til tidligsteTilgjengeligeMåned dersom nødvendig.
        // 2. Dersom alle elementene i lista er før tidligsteTilgjengeligeMåned, returner det yngste.
        return this.last().let {
            Månedssatser(Månedssats(it.virkningstidspunkt, maxOf(it.måned, tidligsteTilgjengeligeMåned), it.verdi))
        }
    }
    return this
        .windowed(size = 2, step = 1, partialWindows = true)
        .flatMap { satser ->
            if (satser.size == 1) {
                // Må ha med sisteelementet
                satser.first().let {
                    listOf(Månedssats(it.virkningstidspunkt, it.måned, it.verdi))
                }
            } else {
                val (tidligste, seneste) = satser
                // Fjerner de parene hvor begge er tidligere enn første måned
                if (seneste.måned >= tidligsteTilgjengeligeMåned) {
                    val tidligsteMåned = maxOf(tidligste.måned, tidligsteTilgjengeligeMåned)
                    tidligsteMåned.until(seneste.måned).map {
                        Månedssats(tidligste.virkningstidspunkt, it, tidligste.verdi)
                    }
                } else {
                    emptyList()
                }
            }
        }.let { Månedssatser(it) }
}

data class RåSatser<T>(
    val satser: NonEmptyList<RåSats<T>>,
) : List<RåSats<T>> by satser {
    constructor(sats: RåSats<T>) : this(nonEmptyListOf(sats))

    init {
        require(this.map { it.virkningstidspunkt }.erSortertOgUtenDuplikater()) {
            // Dersom vi fjerner denne, trenger vi et konsept om kunngjøringsdato eller en form for siste element har presedens over første.
            "Datoene må være sortert i stigende rekkefølge og uten duplikater: ${this.map { it.virkningstidspunkt }}"
        }
    }
}

data class RåSats<T>(
    val virkningstidspunkt: LocalDate,
    val verdi: T,
) {
    val måned = Måned.fra(virkningstidspunkt)

    init {
        require(virkningstidspunkt.erFørsteDagIMåned()) { "Kan kun periodisere datoer hvor virkningstidspunkt er første dag i måneden." }
    }
}

data class Månedssatser<T>(
    val satser: NonEmptyList<Månedssats<T>>,
) : List<Månedssats<T>> by satser {
    constructor(sats: Månedssats<T>) : this(nonEmptyListOf(sats))
    constructor(satser: List<Månedssats<T>>) : this(
        satser.toNonEmptyList<Månedssats<T>>(),
    )

    init {
        satser.map { it.måned }.let {
            require(it.erSammenhengendeSortertOgUtenDuplikater()) {
                "Kunne ikke periodisere. Sammenhengende: ${it.erSammenhengende()}, duplikate: ${it.harDuplikater()}, sortert: ${it.erSortert()}"
            }
        }
    }
}

data class Månedssats<T>(
    val virkningstidspunkt: LocalDate,
    val måned: Måned,
    val verdi: T,
) {
    init {
        require(virkningstidspunkt.erFørsteDagIMåned()) { "Kan kun periodisere datoer hvor virkningstidspunkt er første dag i måneden." }
        require(måned >= Måned.fra(virkningstidspunkt))
    }
}
