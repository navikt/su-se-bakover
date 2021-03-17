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
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.web.FnrGenerator
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
    internal val ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
        fnr = Fnr("17087524256"),
        navn = Person.Navn("fornavn", null, "etternavn"),
        kjønn = null,
        fødselsdato = LocalDate.of(1975, 8, 17),
        adressebeskyttelse = null,
        skjermet = null
    )

    internal fun innvilgetSøknadsbehandling() = Søknadsbehandling.Iverksatt.Innvilget(
        id = behandlingId,
        opprettet = Tidspunkt.EPOCH,
        sakId = sakId,
        saksnummer = saksnummer,
        søknad = journalførtSøknadMedOppgave,
        oppgaveId = oppgaveId,
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
                ektefelle = ektefelle,
                delerBolig = false,
                ektemakeEllerSamboerUførFlyktning = false,
                begrunnelse = null
            ),
            ektefelle = ektefelle
        ),
        fnr = fnr,
        beregning = TestBeregning,
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "navn",
            datoBeregnet = LocalDate.EPOCH,
            nettoBeløp = 1000,
            periodeList = listOf()
        ),
        saksbehandler = NavIdentBruker.Saksbehandler("pro-saksbehandler"),
        attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("kjella")),
        utbetalingId = UUID30.randomUUID(),
        grunnlagsdata = Grunnlagsdata.EMPTY,
    )
}
