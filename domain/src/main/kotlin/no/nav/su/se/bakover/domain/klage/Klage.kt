package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.time.Clock
import java.util.UUID

sealed class Klage {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val sakId: UUID
    abstract val journalpostId: JournalpostId
    abstract val saksbehandler: NavIdentBruker.Saksbehandler

    abstract fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage,
    ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage>

    companion object {
        fun ny(
            sakId: UUID,
            journalpostId: JournalpostId,
            saksbehandler: NavIdentBruker.Saksbehandler,
            clock: Clock,
        ) = OpprettetKlage.create(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            sakId = sakId,
            journalpostId = journalpostId,
            saksbehandler = saksbehandler,
        )
    }
}
