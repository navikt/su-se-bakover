package no.nav.su.se.bakover.test.søknad

import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.mapSecond
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.avvisSøknadMedBrev
import no.nav.su.se.bakover.test.avvisSøknadUtenBrev
import no.nav.su.se.bakover.test.bortfallSøknad
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.trekkSøknad
import no.nav.su.se.bakover.test.veileder
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.LinkedList
import java.util.UUID

val søknadId: UUID = UUID.randomUUID()
val journalpostIdSøknad = JournalpostId("journalpostIdSøknad")
val oppgaveIdSøknad = OppgaveId("oppgaveIdSøknad")

/** NySak med Søknad.Ny som ikke er journalført eller laget oppgave for enda*/
fun nySakMedNySøknad(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    søknadId: UUID = no.nav.su.se.bakover.test.søknad.søknadId,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    søknadInnsendtAv: NavIdentBruker = veileder,
    søknadInnhold: SøknadInnhold = søknadinnholdUføre(personopplysninger = personopplysninger(fnr)),
    clock: Clock = fixedClock,
): Pair<Sak, Søknad.Ny> {
    require(fnr == søknadInnhold.personopplysninger.fnr) { "Fnr i søknadInnhold må være lik fnr" }
    return SakFactory(
        uuidFactory = object : UUIDFactory() {
            val ids = LinkedList(listOf(sakId, søknadId))
            override fun newUUID(): UUID {
                return ids.pop()
            }
        },
        clock = clock,
    ).nySakMedNySøknad(
        fnr = fnr,
        søknadInnhold = søknadInnhold,
        innsendtAv = søknadInnsendtAv,
    ).let {
        require(it.id == sakId)
        require(it.søknad.id == søknadId)
        Pair(it.toSak(saksnummer, Hendelsesversjon(1)), it.søknad)
    }
}

fun nySøknadPåEksisterendeSak(
    eksisterendeSak: Sak,
    søknadId: UUID = UUID.randomUUID(),
    søknadInnsendtAv: NavIdentBruker = veileder,
    søknadInnhold: SøknadInnhold = søknadinnholdUføre(personopplysninger = personopplysninger(eksisterendeSak.fnr)),
    clock: Clock = fixedClock,
): Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> {
    return nySøknadJournalførtMedOppgave(
        clock = clock,
        søknadInnhold = søknadInnhold,
        søknadInnsendtAv = søknadInnsendtAv,
        søknadId = søknadId,
        sakId = eksisterendeSak.id,
    ).let {
        Pair(
            eksisterendeSak.copy(
                søknader = eksisterendeSak.søknader + it,
            ),
            it,
        )
    }
}

fun trukketSøknad(): Pair<Sak, Søknad.Journalført.MedOppgave.Lukket.TrukketAvSøker> {
    return nySakMedjournalførtSøknadOgOppgave().mapSecond {
        it.lukk(
            lukkSøknadCommand = trekkSøknad(
                søknadId = it.id,
                lukketTidspunkt = fixedTidspunkt.plus(1, ChronoUnit.SECONDS),
            ),
        ) as Søknad.Journalført.MedOppgave.Lukket.TrukketAvSøker
    }
}

fun bortfaltSøknad(): Pair<Sak, Søknad.Journalført.MedOppgave.Lukket.Bortfalt> {
    return nySakMedjournalførtSøknadOgOppgave().mapSecond {
        it.lukk(
            lukkSøknadCommand = bortfallSøknad(
                søknadId = it.id,
                lukketTidspunkt = fixedTidspunkt.plus(1, ChronoUnit.SECONDS),
            ),
        ) as Søknad.Journalført.MedOppgave.Lukket.Bortfalt
    }
}

fun avvistSøknadUtenBrev(): Pair<Sak, Søknad.Journalført.MedOppgave.Lukket.Avvist> {
    return nySakMedjournalførtSøknadOgOppgave().mapSecond {
        it.lukk(
            lukkSøknadCommand = avvisSøknadUtenBrev(
                søknadId = it.id,
                lukketTidspunkt = fixedTidspunkt.plus(1, ChronoUnit.SECONDS),
            ),
        ) as Søknad.Journalført.MedOppgave.Lukket.Avvist
    }
}

fun avvistSøknadMedInformasjonsbrev(
    søknadId: UUID = UUID.randomUUID(),
    lukketTidspunkt: Tidspunkt = fixedTidspunkt.plus(1, ChronoUnit.SECONDS),
    lukketAv: NavIdentBruker.Saksbehandler = saksbehandler,
    brevvalg: Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst(
        "informasjonsbrev med fritekst",
    ),
): Søknad.Journalført.MedOppgave.Lukket.Avvist {
    return nySakMedjournalførtSøknadOgOppgave(
        søknadId = søknadId,
    ).second.let {
        it.lukk(
            lukkSøknadCommand = avvisSøknadMedBrev(
                søknadId = søknadId,
                lukketTidspunkt = lukketTidspunkt,
                brevvalg = brevvalg,
                saksbehandler = lukketAv,
            ),
        ) as Søknad.Journalført.MedOppgave.Lukket.Avvist
    }
}

fun avvistSøknadMedVedtaksbrev(
    søknadId: UUID = UUID.randomUUID(),
    lukketTidspunkt: Tidspunkt = fixedTidspunkt.plus(1, ChronoUnit.SECONDS),
    lukketAv: NavIdentBruker.Saksbehandler = saksbehandler,
    brevvalg: Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst = Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(null, "fritekst for avvisning"),
): Søknad.Journalført.MedOppgave.Lukket.Avvist {
    return nySakMedjournalførtSøknadOgOppgave(
        søknadId = søknadId,
    ).second.let {
        it.lukk(
            lukkSøknadCommand = avvisSøknadMedBrev(
                søknadId = søknadId,
                lukketTidspunkt = lukketTidspunkt,
                brevvalg = brevvalg,
                saksbehandler = lukketAv,
            ),
        ) as Søknad.Journalført.MedOppgave.Lukket.Avvist
    }
}

fun nySakMedJournalførtSøknadUtenOppgave(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    søknadId: UUID = no.nav.su.se.bakover.test.søknad.søknadId,
    journalpostId: JournalpostId = journalpostIdSøknad,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    clock: Clock = fixedClock,
    søknadInnhold: SøknadInnhold = søknadinnholdUføre(personopplysninger = personopplysninger(fnr)),
): Pair<Sak, Søknad.Journalført.UtenOppgave> {
    require(fnr == søknadInnhold.personopplysninger.fnr) { "Fnr i søknadInnhold må være lik fnr" }
    return nySakMedNySøknad(
        saksnummer = saksnummer,
        sakId = sakId,
        søknadId = søknadId,
        fnr = fnr,
        clock = clock,
        søknadInnhold = søknadInnhold,
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
    søknadId: UUID = no.nav.su.se.bakover.test.søknad.søknadId,
    journalpostId: JournalpostId = journalpostIdSøknad,
    oppgaveId: OppgaveId = oppgaveIdSøknad,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    clock: Clock = fixedClock,
    søknadInnhold: SøknadInnhold = søknadinnholdUføre(personopplysninger = personopplysninger(fnr)),
): Pair<Sak, Søknad.Journalført.MedOppgave.IkkeLukket> {
    require(fnr == søknadInnhold.personopplysninger.fnr) { "Fnr i søknadInnhold må være lik fnr" }
    return nySakMedJournalførtSøknadUtenOppgave(
        saksnummer = saksnummer,
        sakId = sakId,
        søknadId = søknadId,
        journalpostId = journalpostId,
        fnr = fnr,
        clock = clock,
        søknadInnhold = søknadInnhold,
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

fun nySøknad(
    søknadId: UUID = UUID.randomUUID(),
    clock: Clock = fixedClock,
    sakId: UUID,
    søknadInnhold: SøknadInnhold,
    søknadInnsendtAv: NavIdentBruker = veileder,
): Søknad.Ny {
    return Søknad.Ny(
        id = søknadId,
        opprettet = Tidspunkt.now(clock),
        sakId = sakId,
        søknadInnhold = søknadInnhold,
        innsendtAv = søknadInnsendtAv,
    )
}

fun nySøknadJournalført(
    clock: Clock = fixedClock,
    sakId: UUID,
    søknadInnhold: SøknadInnhold,
    søknadId: UUID = UUID.randomUUID(),
    søknadInnsendtAv: NavIdentBruker = veileder,
): Søknad.Journalført.UtenOppgave {
    return nySøknad(
        clock = clock,
        sakId = sakId,
        søknadInnhold = søknadInnhold,
        søknadId = søknadId,
        søknadInnsendtAv = søknadInnsendtAv,
    ).journalfør(journalpostIdSøknad)
}

/**
 * @param fnr brukes kun dersom [søknadInnhold] ikke sendes inn.
 */
fun nySøknadJournalførtMedOppgave(
    clock: Clock = fixedClock,
    sakId: UUID,
    fnr: Fnr = Fnr.generer(),
    søknadInnhold: SøknadInnhold = søknadinnholdUføre(personopplysninger = personopplysninger(fnr)),
    søknadId: UUID = UUID.randomUUID(),
    søknadInnsendtAv: NavIdentBruker = veileder,
): Søknad.Journalført.MedOppgave.IkkeLukket {
    return nySøknadJournalført(
        clock = clock,
        sakId = sakId,
        søknadInnhold = søknadInnhold,
        søknadId = søknadId,
        søknadInnsendtAv = søknadInnsendtAv,
    ).medOppgave(oppgaveIdSøknad)
}

fun nySakMedLukketSøknad(
    søknadId: UUID = UUID.randomUUID(),
    lukkSøknadCommand: LukkSøknadCommand = trekkSøknad(
        søknadId = søknadId,
        lukketTidspunkt = fixedTidspunkt.plus(1, ChronoUnit.SECONDS),
    ),
): Pair<Sak, Søknad.Journalført.MedOppgave.Lukket> {
    val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave(
        søknadId = søknadId,
    )
    val lukketSøknad = søknad.lukk(
        lukkSøknadCommand = lukkSøknadCommand,
    )

    return sak.copy(
        søknader = listOf(lukketSøknad),
    ) to lukketSøknad
}
