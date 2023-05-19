package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

/**
 * Et overbygg av en liste med [ProsessertKlageinstanshendelse]
 * Holder hendelsene sortert på opprettet-tidspunkt.
 */
data class Klageinstanshendelser private constructor(
    private val underlying: List<ProsessertKlageinstanshendelse>,
) : List<ProsessertKlageinstanshendelse> by underlying {
    companion object {
        fun empty() = Klageinstanshendelser(emptyList())

        fun create(vedtattUtfall: List<ProsessertKlageinstanshendelse>): Klageinstanshendelser {
            return Klageinstanshendelser(vedtattUtfall.sortedBy { it.opprettet.instant })
        }
    }

    fun leggTilNyttVedtak(vedtattUtfall: ProsessertKlageinstanshendelse): Klageinstanshendelser {
        assert(this.all { it.opprettet.instant < vedtattUtfall.opprettet.instant }) {
            "Kan ikke legge til ett vedtak som er eldre enn det forrige vedtaket"
        }

        return create(vedtattUtfall = this + vedtattUtfall)
    }
}

data class TolketKlageinstanshendelse(
    val id: UUID,
    val opprettet: Tidspunkt,
    val avsluttetTidspunkt: Tidspunkt,
    val klageId: UUID,
    val utfall: KlageinstansUtfall,
    val journalpostIDer: List<JournalpostId>,
) {
    fun tilProsessert(oppgaveId: OppgaveId?) = ProsessertKlageinstanshendelse(
        id = id,
        opprettet = opprettet,
        klageId = klageId,
        utfall = utfall,
        journalpostIDer = journalpostIDer,
        oppgaveId = oppgaveId,
    )
}

data class ProsessertKlageinstanshendelse(
    val id: UUID,
    val opprettet: Tidspunkt,
    val klageId: UUID,
    val utfall: KlageinstansUtfall,
    /** Dersom Klageinstansen har sendt ut et eller flere brev */
    val journalpostIDer: List<JournalpostId>,
    val oppgaveId: OppgaveId?,
)

enum class KlageinstansUtfall {
    TRUKKET,
    RETUR,
    OPPHEVET,
    MEDHOLD,
    DELVIS_MEDHOLD,
    STADFESTELSE,
    UGUNST,
    AVVIST,
}

sealed interface KunneIkkeTolkeKlageinstanshendelse {
    object KunneIkkeDeserialisere : KunneIkkeTolkeKlageinstanshendelse
    object UgyldigeVerdier : KunneIkkeTolkeKlageinstanshendelse

    // TODO jah: Vi bør legge inn støtte for anke hendelser når de begynner å dukke opp.
    object AnkehendelserStøttesIkke : KunneIkkeTolkeKlageinstanshendelse
}
