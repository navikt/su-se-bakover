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

data class OversendtKlage private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val saksnummer: Saksnummer,
    override val fnr: Fnr,
    override val journalpostId: JournalpostId,
    override val oppgaveId: OppgaveId,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val datoKlageMottatt: LocalDate,
    val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
    val vurderinger: VurderingerTilKlage.Utfylt,
    val attesteringer: Attesteringshistorikk,
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
            saksbehandler: NavIdentBruker.Saksbehandler,
            vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
            vurderinger: VurderingerTilKlage.Utfylt,
            attesteringer: Attesteringshistorikk,
            datoKlageMottatt: LocalDate,
        ): OversendtKlage {
            return OversendtKlage(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
                vurderinger = vurderinger,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt
            )
        }
    }
}

sealed class KunneIkkeOversendeKlage {
    object FantIkkeKlage : KunneIkkeOversendeKlage()
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) : KunneIkkeOversendeKlage()
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeOversendeKlage()
    data class KunneIkkeLageBrev(val feil: KunneIkkeLageBrevForKlage) : KunneIkkeOversendeKlage()
    object FantIkkeJournalpostIdKnyttetTilVedtaket : KunneIkkeOversendeKlage()
    object KunneIkkeOversendeTilKlageinstans : KunneIkkeOversendeKlage()
}
