package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.left
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
    override val klagevedtakshistorikk: Klagevedtakshistorikk,
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
            klagevedtakshistorikk: Klagevedtakshistorikk,
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
                datoKlageMottatt = datoKlageMottatt,
                klagevedtakshistorikk = klagevedtakshistorikk,
            )
        }
    }

    override fun leggTilNyttKlagevedtak(
        uprosessertKlageinstansVedtak: UprosessertKlageinstansvedtak,
        lagOppgaveCallback: () -> Either<KunneIkkeLeggeTilNyttKlageinstansVedtak, OppgaveId>,
    ): Either<KunneIkkeLeggeTilNyttKlageinstansVedtak, Klage> {
        return when (uprosessertKlageinstansVedtak.utfall) {
            KlagevedtakUtfall.AVVIST,
            KlagevedtakUtfall.TRUKKET,
            KlagevedtakUtfall.STADFESTELSE,
            -> lagOppgaveCallback().map { oppgaveId ->
                leggTilKlagevedtakshistorikk(uprosessertKlageinstansVedtak.tilProsessert(oppgaveId))
            }
            KlagevedtakUtfall.RETUR -> {
                lagOppgaveCallback().map { oppgaveId ->
                    leggTilKlagevedtakshistorikk(uprosessertKlageinstansVedtak.tilProsessert(oppgaveId)).toBekreftet(oppgaveId)
                }
            }
            KlagevedtakUtfall.OPPHEVET,
            KlagevedtakUtfall.MEDHOLD,
            KlagevedtakUtfall.DELVIS_MEDHOLD,
            KlagevedtakUtfall.UGUNST,
            -> KunneIkkeLeggeTilNyttKlageinstansVedtak.IkkeStøttetUtfall.left()
        }
    }

    private fun toBekreftet(oppgaveId: OppgaveId) =
        VurdertKlage.Bekreftet.create(
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
            datoKlageMottatt = datoKlageMottatt,
            klagevedtakshistorikk = klagevedtakshistorikk,
        )

    private fun leggTilKlagevedtakshistorikk(prosessertKlageinstansVedtak: ProsessertKlageinstansvedtak): OversendtKlage =
        this.copy(klagevedtakshistorikk = this.klagevedtakshistorikk.leggTilNyttVedtak(prosessertKlageinstansVedtak))
}

sealed class KunneIkkeOversendeKlage {
    object FantIkkeKlage : KunneIkkeOversendeKlage()
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) :
        KunneIkkeOversendeKlage()

    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeOversendeKlage()
    data class KunneIkkeLageBrev(val feil: KunneIkkeLageBrevForKlage) : KunneIkkeOversendeKlage()
    object FantIkkeJournalpostIdKnyttetTilVedtaket : KunneIkkeOversendeKlage()
    object KunneIkkeOversendeTilKlageinstans : KunneIkkeOversendeKlage()
}
