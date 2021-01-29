package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.behandlingFactory
import java.time.LocalDate
import java.util.UUID

object BehandlingTestUtils {
    internal val sakId = UUID.randomUUID()
    internal val saksnummer = Saksnummer(0)
    internal val søknadId = UUID.randomUUID()
    internal val behandlingId = UUID.randomUUID()
    internal val søknadInnhold = SøknadInnholdTestdataBuilder.build()
    internal val oppgaveId = OppgaveId("o")
    internal val journalpostId = JournalpostId("j")
    internal val journalførtSøknadMedOppgave = Søknad.Journalført.MedOppgave(
        sakId = sakId,
        opprettet = Tidspunkt.EPOCH,
        id = søknadId,
        søknadInnhold = søknadInnhold,
        oppgaveId = oppgaveId,
        journalpostId = journalpostId,
    )
    internal val fnr = FnrGenerator.random()

    /**
     * Behandling er fremdeles muterbar, så vi må påse at testene får hver sin versjon
     */
    internal fun nyBehandling(): Behandling = behandlingFactory.createBehandling(
        id = behandlingId,
        behandlingsinformasjon = Behandlingsinformasjon(
            uførhet = Behandlingsinformasjon.Uførhet(
                status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                uføregrad = 20,
                forventetInntekt = 10,
                begrunnelse = null
            ),
            flyktning = Behandlingsinformasjon.Flyktning(
                status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
                begrunnelse = null
            ),
            lovligOpphold = Behandlingsinformasjon.LovligOpphold(
                status = Behandlingsinformasjon.LovligOpphold.Status.VilkårOppfylt,
                begrunnelse = null
            ),
            fastOppholdINorge = Behandlingsinformasjon.FastOppholdINorge(
                status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
                begrunnelse = null
            ),
            institusjonsopphold = Behandlingsinformasjon.Institusjonsopphold(
                status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårOppfylt,
                begrunnelse = null
            ),
            oppholdIUtlandet = Behandlingsinformasjon.OppholdIUtlandet(
                status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
                begrunnelse = null
            ),
            formue = Behandlingsinformasjon.Formue(
                status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
                borSøkerMedEPS = true,
                verdier = Behandlingsinformasjon.Formue.Verdier(
                    verdiIkkePrimærbolig = 0,
                    verdiEiendommer = 0,
                    verdiKjøretøy = 0,
                    innskudd = 0,
                    verdipapir = 0,
                    pengerSkyldt = 0,
                    kontanter = 0,
                    depositumskonto = 0
                ),
                epsVerdier = Behandlingsinformasjon.Formue.Verdier(
                    verdiIkkePrimærbolig = 0,
                    verdiEiendommer = 0,
                    verdiKjøretøy = 0,
                    innskudd = 0,
                    verdipapir = 0,
                    pengerSkyldt = 0,
                    kontanter = 0,
                    depositumskonto = 0
                ),
                begrunnelse = null
            ),
            personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
                status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
                begrunnelse = null
            ),
            bosituasjon = Behandlingsinformasjon.Bosituasjon(
                epsAlder = null,
                delerBolig = false,
                ektemakeEllerSamboerUførFlyktning = false,
                begrunnelse = null
            ),
            ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
                fnr = Fnr("17087524256"),
                navn = Person.Navn("fornavn", null, "etternavn"),
                kjønn = null,
                fødselsdato = LocalDate.of(1975, 8, 17),
                adressebeskyttelse = null,
                skjermet = null
            )
        ),
        søknad = journalførtSøknadMedOppgave,
        beregning = TestBeregning,
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "navn",
            datoBeregnet = LocalDate.EPOCH,
            nettoBeløp = 1000,
            periodeList = listOf()
        ),
        attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("kjella")),
        saksbehandler = NavIdentBruker.Saksbehandler("pro-saksbehandler"),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        oppgaveId = oppgaveId
    )

    internal fun nySøknadsbehandling() = nyBehandling().let {
        Søknadsbehandling.Iverksatt.Innvilget(
            id = it.id,
            opprettet = it.opprettet,
            sakId = it.sakId,
            saksnummer = it.saksnummer,
            søknad = it.søknad,
            oppgaveId = it.oppgaveId(),
            behandlingsinformasjon = it.behandlingsinformasjon(),
            fnr = it.fnr,
            beregning = it.beregning()!!,
            simulering = it.simulering()!!,
            saksbehandler = it.saksbehandler()!!,
            attestering = it.attestering()!!,
            utbetalingId = UUID30.randomUUID()
        )
    }
}
