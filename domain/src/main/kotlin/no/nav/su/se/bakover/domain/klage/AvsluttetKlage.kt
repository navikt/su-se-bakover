package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Representerer en feilregistrert klage. Eksempler kan være:
 * - Klagen var tillagt feil person.
 * - Klagen var allerede registrert fra før.
 * - Journalpost eller dato NAV mottok kloagen på er feilregistrert.
 * - Klagen ble håndtert på annet vis. F.eks. manuelt via Gosys.
 */
data class AvsluttetKlage(
    val forrigeSteg: Klage,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    val begrunnelse: String,
    val tidspunktAvsluttet: Tidspunkt,
) : Klage by forrigeSteg {

    override fun erÅpen() = false

    override fun kanAvsluttes() = false

    override fun avslutt(
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
        tidspunktAvsluttet: Tidspunkt,
    ) = KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()

    override fun getFritekstTilBrev() = KunneIkkeHenteFritekstTilBrev.UgyldigTilstand(this::class).left()

    override fun lagBrevRequest(
        hentNavnForNavIdent: (saksbehandler: NavIdentBruker.Saksbehandler) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
        hentVedtakDato: (klageId: UUID) -> LocalDate?,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
        clock: Clock,
    ) = KunneIkkeLageBrevRequest.UgyldigTilstand(this::class).left()

    override fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage,
    ) = KunneIkkeVilkårsvurdereKlage.UgyldigTilstand(this::class, VilkårsvurdertKlage::class).left()

    override fun bekreftVilkårsvurderinger(
        saksbehandler: NavIdentBruker.Saksbehandler,
    ) = KunneIkkeBekrefteKlagesteg.UgyldigTilstand(this::class, VilkårsvurdertKlage.Bekreftet::class).left()

    override fun vurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vurderinger: VurderingerTilKlage,
    ) = KunneIkkeVurdereKlage.UgyldigTilstand(this::class, VurdertKlage::class).left()

    override fun bekreftVurderinger(
        saksbehandler: NavIdentBruker.Saksbehandler,
    ) = KunneIkkeBekrefteKlagesteg.UgyldigTilstand(this::class, VurdertKlage.Bekreftet::class).left()

    override fun leggTilAvvistFritekstTilBrev(
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ) = KunneIkkeLeggeTilFritekstForAvvist.UgyldigTilstand(this::class, AvvistKlage::class).left()

    override fun sendTilAttestering(
        saksbehandler: NavIdentBruker.Saksbehandler,
        opprettOppgave: () -> Either<KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave, OppgaveId>,
    ) = KunneIkkeSendeTilAttestering.UgyldigTilstand(this::class, KlageTilAttestering::class).left()

    override fun underkjenn(
        underkjentAttestering: Attestering.Underkjent,
        opprettOppgave: () -> Either<KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave, OppgaveId>,
    ) = KunneIkkeUnderkjenne.UgyldigTilstand(this::class, VurdertKlage.Bekreftet::class).left()

    override fun oversend(
        iverksattAttestering: Attestering.Iverksatt,
    ) = KunneIkkeOversendeKlage.UgyldigTilstand(this::class, OversendtKlage::class).left()

    override fun leggTilNyKlageinstanshendelse(
        tolketKlageinstanshendelse: TolketKlageinstanshendelse,
        lagOppgaveCallback: () -> Either<Klage.KunneIkkeLeggeTilNyKlageinstansHendelse, OppgaveId>,
    ) = Klage.KunneIkkeLeggeTilNyKlageinstansHendelse.MåVæreEnOversendtKlage(menVar = this::class).left()
}
