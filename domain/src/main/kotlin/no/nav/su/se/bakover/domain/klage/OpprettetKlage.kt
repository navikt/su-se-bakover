package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.LocalDate
import java.util.UUID

data class OpprettetKlage private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val saksnummer: Saksnummer,
    override val fnr: Fnr,
    override val journalpostId: JournalpostId,
    override val oppgaveId: OppgaveId,
    override val datoKlageMottatt: LocalDate,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
) : Klage() {

    companion object {
        fun create(
            id: UUID,
            opprettet: Tidspunkt,
            sakId: UUID,
            saksnummer: Saksnummer,
            fnr: Fnr,
            journalpostId: JournalpostId,
            oppgaveId: OppgaveId,
            datoKlageMottatt: LocalDate,
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): OpprettetKlage = OpprettetKlage(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            journalpostId = journalpostId,
            oppgaveId = oppgaveId,
            datoKlageMottatt = datoKlageMottatt,
            saksbehandler = saksbehandler,
        )
    }

    override fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage,
    ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        return when (vilkårsvurderinger) {
            is VilkårsvurderingerTilKlage.Utfylt -> vilkårsvurder(saksbehandler, vilkårsvurderinger)
            is VilkårsvurderingerTilKlage.Påbegynt -> vilkårsvurder(saksbehandler, vilkårsvurderinger)
        }.right()
    }

    fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage.Påbegynt,
    ): VilkårsvurdertKlage.Påbegynt {
        return VilkårsvurdertKlage.Påbegynt.create(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            journalpostId = journalpostId,
            oppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
            vilkårsvurderinger = vilkårsvurderinger,
            vurderinger = null,
            attesteringer = Attesteringshistorikk.empty(),
            datoKlageMottatt = datoKlageMottatt
        )
    }

    fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
    ): VilkårsvurdertKlage.Utfylt {
        return VilkårsvurdertKlage.Utfylt.create(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            journalpostId = journalpostId,
            oppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
            vilkårsvurderinger = vilkårsvurderinger,
            vurderinger = null,
            attesteringer = Attesteringshistorikk.empty(), datoKlageMottatt = datoKlageMottatt
        )
    }
}
