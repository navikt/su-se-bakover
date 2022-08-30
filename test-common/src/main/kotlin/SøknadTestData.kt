package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadinnhold.ForNav
import no.nav.su.se.bakover.domain.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadInnhold
import java.time.Clock
import java.util.LinkedList
import java.util.UUID

val søknadId: UUID = UUID.randomUUID()
val journalpostIdSøknad = JournalpostId("journalpostIdSøknad")
val oppgaveIdSøknad = OppgaveId("oppgaveIdSøknad")

fun søknadinnhold(
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    forNav: ForNav = SøknadInnholdTestdataBuilder.build().forNav,
) = SøknadInnholdTestdataBuilder.build(
    personopplysninger = Personopplysninger(fnr),
    forNav = forNav,
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
    søknadInnhold = søknadinnhold(fnr),
).let {
    assert(it.id == sakId)
    assert(it.søknad.id == søknadId)
    Pair(it.toSak(saksnummer), it.søknad)
}

@Suppress("unused")
val trukketSøknad = nySakMedjournalførtSøknadOgOppgave().second.lukk(
    lukketAv = saksbehandler,
    type = Søknad.Journalført.MedOppgave.Lukket.LukketType.TRUKKET,
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
    klager: List<Klage> = emptyList(),
): Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> {
    klager.forEach {
        assert(it.sakId == sakId) { "Klagenes sakId må være identisk med sakens id." }
    }
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
                klager = klager,
            ),
            journalførtSøknadMedOppgave,
        )
    }
}

fun nySøknad(
    clock: Clock = fixedClock,
    sakId: UUID,
    søknadInnhold: SøknadInnhold,
): Søknad.Ny {
    return Søknad.Ny(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(clock),
        sakId = sakId,
        søknadInnhold = søknadInnhold,
    )
}

fun nySøknadJournalført(
    clock: Clock = fixedClock,
    sakId: UUID,
    søknadInnhold: SøknadInnhold,
): Søknad.Journalført.UtenOppgave {
    return nySøknad(
        clock = clock,
        sakId = sakId,
        søknadInnhold = søknadInnhold,
    ).journalfør(journalpostIdSøknad)
}

fun nySøknadJournalførtMedOppgave(
    clock: Clock = fixedClock,
    sakId: UUID,
    søknadInnhold: SøknadInnhold,
): Søknad.Journalført.MedOppgave.IkkeLukket {
    return nySøknadJournalført(
        clock = clock,
        sakId = sakId,
        søknadInnhold = søknadInnhold,
    ).medOppgave(oppgaveIdSøknad)
}

fun nySakMedLukketSøknad(): Pair<Sak, Søknad.Journalført.MedOppgave.Lukket> {
    val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()
    val lukketSøknad = søknad.lukk(
        lukketAv = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
        type = Søknad.Journalført.MedOppgave.Lukket.LukketType.TRUKKET,
        lukketTidspunkt = fixedTidspunkt,
    )

    return sak.copy(
        søknader = listOf(lukketSøknad),
    ) to lukketSøknad
}
