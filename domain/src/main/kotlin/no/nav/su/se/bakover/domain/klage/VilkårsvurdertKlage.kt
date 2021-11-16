package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.util.UUID

sealed class VilkårsvurdertKlage : Klage() {

    abstract val vilkårsvurderinger: VilkårsvurderingerTilKlage

    override fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage,
    ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        return when (vilkårsvurderinger) {
            is VilkårsvurderingerTilKlage.Ferdig -> Ferdig.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                journalpostId = journalpostId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
            )
            is VilkårsvurderingerTilKlage.Påbegynt -> Påbegynt.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                journalpostId = journalpostId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
            )
        }.right()
    }

    data class Påbegynt private constructor(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val journalpostId: JournalpostId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Påbegynt,
    ) : VilkårsvurdertKlage() {

        companion object {
            fun create(
                id: UUID,
                opprettet: Tidspunkt,
                sakId: UUID,
                journalpostId: JournalpostId,
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: VilkårsvurderingerTilKlage.Påbegynt,
            ) = Påbegynt(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                journalpostId = journalpostId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
            )
        }
    }

    data class Ferdig private constructor(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val journalpostId: JournalpostId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Ferdig,
    ) : VilkårsvurdertKlage() {

        companion object {
            fun create(
                id: UUID,
                opprettet: Tidspunkt,
                sakId: UUID,
                journalpostId: JournalpostId,
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: VilkårsvurderingerTilKlage.Ferdig,
            ) = Ferdig(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                journalpostId = journalpostId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
            )
        }
    }
}

sealed class KunneIkkeVilkårsvurdereKlage {
    object FantIkkeKlage : KunneIkkeVilkårsvurdereKlage()
    object FantIkkeVedtak : KunneIkkeVilkårsvurdereKlage()
}
