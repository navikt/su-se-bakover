package no.nav.su.se.bakover.domain.regulering.supplement

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.Clock
import java.util.UUID

/**
 * Et reguleringssupplement er data som mottas fra en ekstern kilde, for eksempel PESYS, som brukes for å justere
 * på utbetalingen. Denne vil være delt opp i en [ReguleringssupplementFor] per person (kan være både brukere og EPS).
 */
data class Reguleringssupplement(
    val id: UUID,
    val opprettet: Tidspunkt,
    private val supplement: List<ReguleringssupplementFor>,
    val originalCsv: String,
) : List<ReguleringssupplementFor> by supplement {

    fun getFor(fnr: Fnr): ReguleringssupplementFor? = this.supplement.singleOrNull { it.fnr == fnr }

    companion object {
        fun empty(clock: Clock) = Reguleringssupplement(UUID.randomUUID(), Tidspunkt.now(clock), emptyList(), "")
    }

    /**
     * Maskerer supplement og originalCsv for logging. Disse har både sensitive data og for mye data til å logges.
     */
    override fun toString(): String {
        return "Reguleringssupplement(id=$id, opprettet=$opprettet, supplement=****, originalCsv=****)"
    }
}

/**
 * Det knyttes et slikt objekt til hver regulering, både manuelle og automatiske, eller null dersom vi ikke har slike data.
 * Den vil være basert på eksterne data (både fil og tjenester). Merk at det er viktig å lagre originaldata, f.eks. i hendelser.
 *
 * @param supplementId Id'en til [Reguleringssupplement] denne ble hentet ut ifra. Den kan være null ved historiske reguleringer.
 * @param bruker reguleringsdata/fradrag fra eksterne kilder for bruker. Kan være null dersom bruker ikke har fradrag fra eksterne kilder.
 * @param eps reguleringsdata/fradrag fra eksterne kilder for ingen, en eller flere EPS, eller vi har hentet regulerte fradrag på EPS.
 */
data class EksternSupplementRegulering(
    val supplementId: UUID?,
    val bruker: ReguleringssupplementFor?,
    // TODO jah - Bør kanskje ha en sjekk på at fnr er unike på tvers av eps og bruker?
    val eps: List<ReguleringssupplementFor>,
) {

    init {
        require(eps.distinctBy { it.fnr } == eps) {
            sikkerLogg.error("Kan ikke ha flere reguleringssupplementFor for samme EPS. eps: ${eps.map { it.toSikkerloggString() }}")
            "Kan ikke ha flere reguleringssupplementFor for samme EPS. Se sikkerlogg for detaljer"
        }
    }

    fun hentForEps(fnr: Fnr): ReguleringssupplementFor? = eps.find { it.fnr == fnr }

    fun toSikkerloggString(): String {
        return "EksternSupplementRegulering(supplementId=$supplementId, bruker=${bruker?.toSikkerloggString()}, eps=${eps.map { it.toSikkerloggString() }})"
    }
}
