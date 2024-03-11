package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.right
import dokument.domain.journalføring.KunneIkkeSjekkeTilknytningTilSak
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.LocalDate
import java.util.UUID

data class OpprettetKlage(
    override val id: KlageId,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val saksnummer: Saksnummer,
    override val fnr: Fnr,
    override val journalpostId: JournalpostId,
    override val oppgaveId: OppgaveId,
    override val datoKlageMottatt: LocalDate,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
) : Klage {

    override val vilkårsvurderinger: VilkårsvurderingerTilKlage? = null

    override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()

    override fun erÅpen() = true

    override fun kanAvsluttes() = true

    override fun avslutt(
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
        tidspunktAvsluttet: Tidspunkt,
    ) = AvsluttetKlage(
        underliggendeKlage = this,
        saksbehandler = saksbehandler,
        begrunnelse = begrunnelse,
        avsluttetTidspunkt = tidspunktAvsluttet,
    ).right()

    override fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage,
    ): Either<Nothing, VilkårsvurdertKlage> {
        return when (vilkårsvurderinger) {
            is VilkårsvurderingerTilKlage.Utfylt -> vilkårsvurder(saksbehandler, vilkårsvurderinger)
            is VilkårsvurderingerTilKlage.Påbegynt -> vilkårsvurder(saksbehandler, vilkårsvurderinger)
        }.right()
    }

    private fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage.Påbegynt,
    ): VilkårsvurdertKlage.Påbegynt {
        return VilkårsvurdertKlage.Påbegynt(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            journalpostId = journalpostId,
            oppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
            vilkårsvurderinger = vilkårsvurderinger,
            attesteringer = Attesteringshistorikk.empty(),
            datoKlageMottatt = datoKlageMottatt,
        )
    }

    private fun vilkårsvurder(
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
            attesteringer = Attesteringshistorikk.empty(),
            datoKlageMottatt = datoKlageMottatt,
            klageinstanshendelser = Klageinstanshendelser.empty(),
            fritekstTilAvvistVedtaksbrev = null,
        )
    }
}

sealed interface KunneIkkeOppretteKlage {
    data object FantIkkeSak : KunneIkkeOppretteKlage
    data object FinnesAlleredeEnÅpenKlage : KunneIkkeOppretteKlage
    data object KunneIkkeOppretteOppgave : KunneIkkeOppretteKlage
    data object UgyldigMottattDato : KunneIkkeOppretteKlage
    data class FeilVedHentingAvJournalpost(val feil: KunneIkkeSjekkeTilknytningTilSak) : KunneIkkeOppretteKlage
}
