package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.mapSecond
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadinnhold.ForNav
import no.nav.su.se.bakover.domain.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.time.Clock
import java.time.temporal.ChronoUnit
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
    søknadInnsendtAv: NavIdentBruker = veileder,
    søknadInnhold: SøknadInnhold = søknadinnhold(fnr),
    clock: Clock = fixedClock,
): Pair<Sak, Søknad.Ny> = SakFactory(
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
    assert(it.id == sakId)
    assert(it.søknad.id == søknadId)
    Pair(it.toSak(saksnummer, Hendelsesversjon(1)), it.søknad)
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
    brevvalg: Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst("informasjonsbrev med fritekst"),
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
    brevvalg: Brevvalg.SaksbehandlersValg.SkalSendeBrev.VedtaksbrevUtenFritekst = Brevvalg.SaksbehandlersValg.SkalSendeBrev.VedtaksbrevUtenFritekst(),
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
    søknadId: UUID = no.nav.su.se.bakover.test.søknadId,
    journalpostId: JournalpostId = journalpostIdSøknad,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    clock: Clock = fixedClock,
): Pair<Sak, Søknad.Journalført.UtenOppgave> {
    return nySakMedNySøknad(
        saksnummer = saksnummer,
        sakId = sakId,
        søknadId = søknadId,
        fnr = fnr,
        clock = clock,
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
    clock: Clock = fixedClock,
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
        clock = clock,
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
