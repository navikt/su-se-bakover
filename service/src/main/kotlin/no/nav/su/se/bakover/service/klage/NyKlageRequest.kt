package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class NyKlageRequest(
    val sakId: UUID,
    private val saksbehandler: NavIdentBruker.Saksbehandler,
    val journalpostId: JournalpostId,
    private val datoKlageMottatt: LocalDate,
    private val clock: Clock,
) {
    fun toKlage(
        saksnummer: Saksnummer,
        fnr: Fnr,
        oppgaveId: OppgaveId,
        clock: Clock,
    ): OpprettetKlage {
        return Klage.ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            journalpostId = journalpostId,
            oppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
            datoKlageMottatt = datoKlageMottatt,
            clock = clock,
        )
    }

    fun validate(): Either<KunneIkkeOppretteKlage, Unit> = when {
        datoKlageMottatt > LocalDate.now(clock) -> KunneIkkeOppretteKlage.UgyldigMottattDato.left()
        else -> Unit.right()
    }
}
