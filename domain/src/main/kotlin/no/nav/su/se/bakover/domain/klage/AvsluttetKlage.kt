package no.nav.su.se.bakover.domain.klage

import arrow.core.left
import behandling.klage.domain.FormkravTilKlage
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.domain.Avbrutt
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
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
    override val id: KlageId,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val avsluttetTidspunkt: Tidspunkt,
    override val opprettet: Tidspunkt,
    override val sakstype: Sakstype,
    override val sakId: UUID,
    override val saksnummer: Saksnummer,
    override val fnr: Fnr,
    override val journalpostId: JournalpostId,
    override val oppgaveId: OppgaveId,
    override val datoKlageMottatt: LocalDate,
    val begrunnelse: String,
) : Klage,
    Avbrutt {
    override val avsluttetAv: NavIdentBruker = saksbehandler

    /*
        Det kan hende at disse feltene ligger i basen om saksbehandler har behandlet klagen og deretter avsluttet den.
        Vil ikke åpne for at de skal brukes etter avklaring med John Are. Men vi kan titte i de hvis behov.
     */
    override val vilkårsvurderinger: FormkravTilKlage
        get() = throw IllegalStateException("Avsluttet klage har ikke attesteringer")
    override val attesteringer: Attesteringshistorikk
        get() = throw IllegalStateException("Avsluttet klage har ikke attesteringer")

    override fun erÅpen() = false
    override fun erAvsluttet() = true
    override fun erAvbrutt() = true

    override fun kanAvsluttes() = false

    override fun avslutt(
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
        tidspunktAvsluttet: Tidspunkt,
    ) = KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
}
