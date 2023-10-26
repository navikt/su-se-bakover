package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode

sealed class KryssjekkFeil(val prioritet: Int) : Comparable<KryssjekkFeil> {
    /**
     * Oppstår i de tilfellene simuleringen i sin helhet gir tom respons (ingen utbetalinger)
     * og vi forventer en form for utbetaling.
     *
     * TODO jah: Her føler jeg implementasjonsspesifikk logikk i simuleringsresponsen har sneket seg inn i domenet vårt.
     */
    data class KombinasjonAvSimulertTypeOgTidslinjeTypeErUgyldig(
        val periode: Periode,
        val simulertType: String,
        val tidslinjeType: String,
    ) : KryssjekkFeil(prioritet = 2)

    data class SimulertBeløpOgTidslinjeBeløpErForskjellig(
        val måned: Måned,
        val simulertBeløp: Int,
        val tidslinjeBeløp: Int,
    ) : KryssjekkFeil(prioritet = 2)

    override fun compareTo(other: KryssjekkFeil): Int {
        return this.prioritet.compareTo(other.prioritet)
    }
}
