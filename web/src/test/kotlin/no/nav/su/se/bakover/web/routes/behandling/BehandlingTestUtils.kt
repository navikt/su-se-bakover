package no.nav.su.se.bakover.web.routes.behandling

import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.web.FnrGenerator
import java.util.UUID

object BehandlingTestUtils {
    internal val sakId = UUID.randomUUID()
    internal val søknadId = UUID.randomUUID()
    internal val behandlingId = UUID.randomUUID()
    internal val søknadInnhold = SøknadInnholdTestdataBuilder.build()
    internal val behandlingFactory = BehandlingFactory(mock())
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
            oppholdIUtlandet = Behandlingsinformasjon.OppholdIUtlandet(
                status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
                begrunnelse = null
            ),
            formue = Behandlingsinformasjon.Formue(
                status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
                borSøkerMedEPS = true,
                verdier = Behandlingsinformasjon.Formue.Verdier(
                    verdiIkkePrimærbolig = 0,
                    verdiKjøretøy = 0,
                    innskudd = 0,
                    verdipapir = 0,
                    pengerSkyldt = 0,
                    kontanter = 0,
                    depositumskonto = 0
                ),
                epsVerdier = Behandlingsinformasjon.Formue.Verdier(
                    verdiIkkePrimærbolig = 0,
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
                epsFnr = null,
                delerBolig = false,
                ektemakeEllerSamboerUførFlyktning = false,
                begrunnelse = null
            ),
            ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
                fnr = Fnr("17087524256"),
                navn = Person.Navn("fornavn", null, "etternavn"),
                kjønn = null,
                adressebeskyttelse = null,
                skjermet = null
            )
        ),
        søknad = journalførtSøknadMedOppgave,
        beregning = TestBeregning,
        attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("kjella")),
        saksbehandler = NavIdentBruker.Saksbehandler("pro-saksbehandler"),
        sakId = sakId,
        fnr = FnrGenerator.random(),
        oppgaveId = oppgaveId
    )
}
