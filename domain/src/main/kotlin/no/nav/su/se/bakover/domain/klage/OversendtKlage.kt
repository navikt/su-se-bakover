package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import java.time.Clock
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
    val klagevedtakshistorikk: Klagevedtakshistorikk,
    val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
    val vurderinger: VurderingerTilKlage.Utfylt,
    val attesteringer: Attesteringshistorikk,
) : Klage {

    override fun getFritekstTilBrev(): Either<KunneIkkeHenteFritekstTilBrev.UgyldigTilstand, String> {
        return vurderinger.fritekstTilBrev.right()
    }

    override fun lagBrevRequest(
        hentNavnForNavIdent: (saksbehandler: NavIdentBruker.Saksbehandler) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
        hentVedtakDato: (klageId: UUID) -> LocalDate?,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
        clock: Clock,
    ): Either<KunneIkkeLageBrevRequest, LagBrevRequest.Klage> {
        return LagBrevRequest.Klage.Oppretthold(
            person = hentPerson(this.fnr).getOrHandle {
                return KunneIkkeLageBrevRequest.FeilVedHentingAvPerson(it).left()
            },
            dagensDato = LocalDate.now(clock),
            saksbehandlerNavn = hentNavnForNavIdent(this.saksbehandler).getOrHandle {
                return KunneIkkeLageBrevRequest.FeilVedHentingAvSaksbehandlernavn(it).left()
            },
            fritekst = this.vurderinger.fritekstTilBrev,
            saksnummer = this.saksnummer,
            klageDato = this.datoKlageMottatt,
            vedtakDato = hentVedtakDato(this.id)
                ?: return KunneIkkeLageBrevRequest.FeilVedHentingAvVedtakDato.left(),
        ).right()
    }

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
        lagOppgaveCallback: () -> Either<Klage.KunneIkkeLeggeTilNyttKlageinstansVedtak, OppgaveId>,
    ): Either<Klage.KunneIkkeLeggeTilNyttKlageinstansVedtak, Klage> {
        return lagOppgaveCallback().map { oppgaveId ->
            val oppdatertKlage = leggTilKlagevedtakshistorikk(uprosessertKlageinstansVedtak.tilProsessert(oppgaveId))

            when (uprosessertKlageinstansVedtak.utfall) {
                KlagevedtakUtfall.TRUKKET,
                KlagevedtakUtfall.OPPHEVET,
                KlagevedtakUtfall.MEDHOLD,
                KlagevedtakUtfall.DELVIS_MEDHOLD,
                KlagevedtakUtfall.STADFESTELSE,
                KlagevedtakUtfall.UGUNST,
                KlagevedtakUtfall.AVVIST -> oppdatertKlage
                KlagevedtakUtfall.RETUR -> oppdatertKlage.toBekreftet(oppgaveId)
            }
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
    data class KunneIkkeLageBrevRequest(val feil: no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevRequest) :
        KunneIkkeOversendeKlage()
}
