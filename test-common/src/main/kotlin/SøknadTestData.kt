package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Personopplysninger
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.LinkedList
import java.util.UUID

val søknadId: UUID = UUID.randomUUID()
val journalpostIdSøknad = JournalpostId("journalpostIdSøknad")
val oppgaveIdSøknad = OppgaveId("oppgaveIdSøknad")

fun søknadinnhold(fnr: Fnr = no.nav.su.se.bakover.test.fnr) = SøknadInnholdTestdataBuilder.build(
    personopplysninger = Personopplysninger(fnr),
)

/** NySak med Søknad.Ny som ikke er journalført eller laget oppgave for enda*/
fun nySakMedNySøknad(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    søknadId: UUID = no.nav.su.se.bakover.test.søknadId,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
): Pair<Sak, Søknad.Ny> = SakFactory(
    uuidFactory = object : UUIDFactory() {
        val ids = LinkedList(listOf(sakId, søknadId))
        override fun newUUID(): UUID {
            return ids.pop()
        }
    },
    clock = fixedClock,
).nySakMedNySøknad(
    fnr = fnr,
    søknadinnhold(fnr),
).let {
    assert(it.id == sakId)
    assert(it.søknad.id == søknadId)
    Pair(it.toSak(saksnummer), it.søknad)
}

@Suppress("unused")
val lukketSøknad = nySakMedNySøknad().second.lukk(
    lukketAv = saksbehandler,
    type = Søknad.Lukket.LukketType.TRUKKET,
    lukketTidspunkt = fixedTidspunkt,
)

fun nySakMedJournalførtSøknadUtenOppgave(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    søknadId: UUID = no.nav.su.se.bakover.test.søknadId,
    journalpostId: JournalpostId = journalpostIdSøknad,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
): Pair<Sak, Søknad.Journalført.UtenOppgave> {
    return nySakMedNySøknad(
        saksnummer = saksnummer,
        sakId = sakId,
        søknadId = søknadId,
        fnr = fnr,
    ).let { (sak, nySøknad) ->
        val journalførtSøknad = nySøknad.journalfør(journalpostId)
        Pair(
            sak.copy(
                søknader = listOf(journalførtSøknad),
            ),
            journalførtSøknad,
        )
    }
}

fun nySakMedjournalførtSøknadOgOppgave(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    søknadId: UUID = no.nav.su.se.bakover.test.søknadId,
    journalpostId: JournalpostId = journalpostIdSøknad,
    oppgaveId: OppgaveId = oppgaveIdSøknad,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
): Pair<Sak, Søknad.Journalført.MedOppgave> {
    return nySakMedJournalførtSøknadUtenOppgave(
        saksnummer = saksnummer,
        sakId = sakId,
        søknadId = søknadId,
        journalpostId = journalpostId,
        fnr = fnr,
    ).let { (sak, journalførtSøknad) ->
        val journalførtSøknadMedOppgave = journalførtSøknad.medOppgave(oppgaveId)
        Pair(
            sak.copy(
                søknader = listOf(journalførtSøknadMedOppgave),
            ),
            journalførtSøknadMedOppgave,
        )
    }
}
