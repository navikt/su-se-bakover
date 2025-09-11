package no.nav.su.se.bakover.domain.klage

import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
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
        require(this.all { it.opprettet.instant < vedtattUtfall.opprettet.instant }) {
            "Kan ikke legge til ett vedtak som er eldre enn det forrige vedtaket"
        }

        return create(vedtattUtfall = this + vedtattUtfall)
    }
}

sealed interface TolketKlageinstanshendelse {

    val id: UUID
    val opprettet: Tidspunkt
    val klageId: KlageId

    fun tilProsessert(oppgaveId: OppgaveId): ProsessertKlageinstanshendelse

    fun erAvsluttetMedRetur(): Boolean

    data class KlagebehandlingAvsluttet(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        val avsluttetTidspunkt: Tidspunkt,
        override val klageId: KlageId,
        val utfall: AvsluttetKlageinstansUtfall,
        val journalpostIDer: List<JournalpostId>,
    ) : TolketKlageinstanshendelse {
        override fun tilProsessert(oppgaveId: OppgaveId) = ProsessertKlageinstanshendelse.KlagebehandlingAvsluttet(
            id = id,
            opprettet = opprettet,
            klageId = klageId,
            utfall = utfall,
            journalpostIDer = journalpostIDer,
            oppgaveId = oppgaveId,
        )

        override fun erAvsluttetMedRetur() = utfall is AvsluttetKlageinstansUtfall.Retur
    }

    data class AnkebehandlingOpprettet(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val klageId: KlageId,
        val mottattKlageinstans: Tidspunkt,
    ) : TolketKlageinstanshendelse {
        override fun tilProsessert(oppgaveId: OppgaveId) = ProsessertKlageinstanshendelse.AnkebehandlingOpprettet(
            id = id,
            opprettet = opprettet,
            klageId = klageId,
            mottattKlageinstans = mottattKlageinstans,
            oppgaveId = oppgaveId,
        )
        override fun erAvsluttetMedRetur() = false
    }

    data class AnkebehandlingAvsluttet(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        val avsluttetTidspunkt: Tidspunkt,
        override val klageId: KlageId,
        val utfall: AvsluttetKlageinstansUtfall,
        val journalpostIDer: List<JournalpostId>,
    ) : TolketKlageinstanshendelse {
        override fun tilProsessert(oppgaveId: OppgaveId) = ProsessertKlageinstanshendelse.AnkebehandlingAvsluttet(
            id = id,
            opprettet = opprettet,
            klageId = klageId,
            utfall = utfall,
            journalpostIDer = journalpostIDer,
            oppgaveId = oppgaveId,
        )
        override fun erAvsluttetMedRetur() = utfall is AvsluttetKlageinstansUtfall.Retur
    }

    data class AnkeITrygderettenOpprettet(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val klageId: KlageId,
        val sendtTilTrygderetten: Tidspunkt,
        val utfall: String,
    ) : TolketKlageinstanshendelse {
        override fun tilProsessert(oppgaveId: OppgaveId) = ProsessertKlageinstanshendelse.AnkebehandlingOpprettet(
            id = id,
            opprettet = opprettet,
            klageId = klageId,
            mottattKlageinstans = sendtTilTrygderetten,
            oppgaveId = oppgaveId,
        )

        override fun erAvsluttetMedRetur() = false
    }

    data class AnkeITrygderettenAvsluttet(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        val avsluttetTidspunkt: Tidspunkt,
        override val klageId: KlageId,
        val utfall: AvsluttetKlageinstansUtfall,
        val journalpostIDer: List<JournalpostId>,
    ) : TolketKlageinstanshendelse {
        override fun tilProsessert(oppgaveId: OppgaveId): ProsessertKlageinstanshendelse.AnkeITrygderettenAvsluttet {
            return ProsessertKlageinstanshendelse.AnkeITrygderettenAvsluttet(
                id = id,
                opprettet = opprettet,
                klageId = klageId,
                utfall = utfall,
                journalpostIDer = journalpostIDer,
                oppgaveId = oppgaveId,
            )
        }
        override fun erAvsluttetMedRetur() = utfall is AvsluttetKlageinstansUtfall.Retur
    }
}

sealed interface ProsessertKlageinstanshendelse {
    val id: UUID
    val opprettet: Tidspunkt
    val klageId: KlageId
    val oppgaveId: OppgaveId

    data class KlagebehandlingAvsluttet(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val klageId: KlageId,
        val utfall: AvsluttetKlageinstansUtfall,
        /** Dersom Klageinstansen har sendt ut et eller flere brev */
        val journalpostIDer: List<JournalpostId>,
        override val oppgaveId: OppgaveId,
    ) : ProsessertKlageinstanshendelse

    data class AnkebehandlingOpprettet(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val klageId: KlageId,
        val mottattKlageinstans: Tidspunkt?,
        override val oppgaveId: OppgaveId,
    ) : ProsessertKlageinstanshendelse

    data class AnkebehandlingAvsluttet(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val klageId: KlageId,
        val utfall: AvsluttetKlageinstansUtfall,
        /** Dersom Klageinstansen har sendt ut et eller flere brev */
        val journalpostIDer: List<JournalpostId>,
        override val oppgaveId: OppgaveId,
    ) : ProsessertKlageinstanshendelse

    data class AnkeITrygderettenOpprettet(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val klageId: KlageId,
        val mottattKlageinstans: Tidspunkt?,
        override val oppgaveId: OppgaveId,
    ) : ProsessertKlageinstanshendelse

    data class AnkeITrygderettenAvsluttet(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val klageId: KlageId,
        val utfall: AvsluttetKlageinstansUtfall,
        /** Dersom Klageinstansen har sendt ut et eller flere brev */
        val journalpostIDer: List<JournalpostId>,
        override val oppgaveId: OppgaveId,
    ) : ProsessertKlageinstanshendelse
}

/** Disse følger Kabal/klageinstansen. Merk at dette er kun avsluttet utfall og ikke oversendelsesutfall (e.g. til trygderetten) */
sealed interface AvsluttetKlageinstansUtfall {
    fun erRetur(): Boolean = this is Retur

    data object Retur : AvsluttetKlageinstansUtfall
    sealed interface KreverHandling : AvsluttetKlageinstansUtfall {
        data object Ugunst : KreverHandling
        data object Opphevet : KreverHandling
        data object Medhold : KreverHandling
        data object DelvisMedhold : KreverHandling
    }

    sealed interface TilInformasjon : AvsluttetKlageinstansUtfall {
        data object Stadfestelse : TilInformasjon
        data object Trukket : TilInformasjon
        data object Avvist : TilInformasjon

        /** Dette er trygderetten er en henvisning til KA om at de må behandle anken på nytt. */
        data object Henvist : TilInformasjon

        /** Dette er tilfeller som ikke er dekket av trukket altså her kommer det ikke etter henvendelse fra bruker men av andre årsaker feks dødsfall */
        data object Henlagt : TilInformasjon
    }
}

sealed interface KunneIkkeTolkeKlageinstanshendelse {
    data object KunneIkkeDeserialisere : KunneIkkeTolkeKlageinstanshendelse
    data object UgyldigeVerdier : KunneIkkeTolkeKlageinstanshendelse

    // TODO jah: Vi bør legge inn støtte for dette når de begynner å dukke opp.
    data object BehandlingFeilregistrertStøttesIkke : KunneIkkeTolkeKlageinstanshendelse
}
