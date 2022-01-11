package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

data class IverksattAvvistKlage private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val saksnummer: Saksnummer,
    override val fnr: Fnr,
    override val journalpostId: JournalpostId,
    override val oppgaveId: OppgaveId,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val datoKlageMottatt: LocalDate,
    val attesteringer: Attesteringshistorikk,
    val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
    val vurderinger: VurderingerTilKlage?,
) : Klage {

    companion object {
        fun create(
            id: UUID,
            opprettet: Tidspunkt,
            sakId: UUID,
            saksnummer: Saksnummer,
            fnr: Fnr,
            journalpostId: JournalpostId,
            oppgaveId: OppgaveId,
            saksbehandler: NavIdentBruker.Saksbehandler,
            vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
            vurderinger: VurderingerTilKlage?,
            attesteringer: Attesteringshistorikk,
            datoKlageMottatt: LocalDate,
        ): IverksattAvvistKlage {
            return IverksattAvvistKlage(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                datoKlageMottatt = datoKlageMottatt,
                attesteringer = attesteringer,
                vilkårsvurderinger = vilkårsvurderinger,
                vurderinger = vurderinger,
            )
        }
    }
}

sealed class KunneIkkeIverksetteAvvistKlage {
    object FantIkkeKlage : KunneIkkeIverksetteAvvistKlage()
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) : KunneIkkeIverksetteAvvistKlage()
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteAvvistKlage()
    data class KunneIkkeLageBrev(val feil: KunneIkkeLageBrevForKlage) : KunneIkkeIverksetteAvvistKlage()
}
