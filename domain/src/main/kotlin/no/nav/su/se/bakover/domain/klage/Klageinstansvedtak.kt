package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

data class Klagevedtakshistorikk private constructor(
    private val underlying: List<ProsessertKlageinstansvedtak>
) : List<ProsessertKlageinstansvedtak> by underlying {
    companion object {
        fun empty() = Klagevedtakshistorikk(emptyList())

        fun create(vedtattUtfall: List<ProsessertKlageinstansvedtak>): Klagevedtakshistorikk {
            return Klagevedtakshistorikk(vedtattUtfall.sortedBy { it.opprettet.instant })
        }
    }

    fun leggTilNyttVedtak(vedtattUtfall: ProsessertKlageinstansvedtak): Klagevedtakshistorikk {
        assert(this.all { it.opprettet.instant < vedtattUtfall.opprettet.instant }) {
            "Kan ikke legge til ett vedtak som er eldre enn det forrige vedtaket"
        }

        return create(vedtattUtfall = this + vedtattUtfall)
    }
}

/**
 * Representerer ett fattet klagevedtak av Kabal.
 */
data class UprosessertKlageinstansvedtak(
    val id: UUID,
    val opprettet: Tidspunkt,
    val klageId: UUID,
    val utfall: KlagevedtakUtfall,
    val vedtaksbrevReferanse: String,
) {
    fun tilProsessert(oppgaveId: OppgaveId?) = ProsessertKlageinstansvedtak(
        id = id,
        opprettet = opprettet,
        klageId = klageId,
        utfall = utfall,
        vedtaksbrevReferanse = vedtaksbrevReferanse,
        oppgaveId = oppgaveId,
    )
}

data class ProsessertKlageinstansvedtak(
    val id: UUID,
    val opprettet: Tidspunkt,
    val klageId: UUID,
    val utfall: KlagevedtakUtfall,
    val vedtaksbrevReferanse: String,
    val oppgaveId: OppgaveId?,
)

enum class KlagevedtakUtfall {
    TRUKKET,
    RETUR,
    OPPHEVET,
    MEDHOLD,
    DELVIS_MEDHOLD,
    STADFESTELSE,
    UGUNST,
    AVVIST
}

sealed interface KanIkkeTolkeKlagevedtak {
    object KunneIkkeDeserialisere : KanIkkeTolkeKlagevedtak
    object UgyldigeVerdier : KanIkkeTolkeKlagevedtak
}
