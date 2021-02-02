package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Person.Navn
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.Bosituasjon
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.FastOppholdINorge
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.Flyktning
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.Flyktning.Status
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.Formue
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.Formue.Verdier
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.LovligOpphold
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.OppholdIUtlandet
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.PersonligOppmøte
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.Uførhet
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.util.UUID

object BehandlingTestUtils {

    internal val tidspunkt = Tidspunkt.now(fixedClock)
    internal val behandlingFactory = BehandlingFactory(mock(), fixedClock)
    internal val observerMock: EventObserver by lazy {
        mock { on { handle(any()) }.doNothing() }
    }

    internal val sakId: UUID = UUID.fromString("268e62fb-3079-4e8d-ab32-ff9fb9eac2ec")
    internal val saksnummer = Saksnummer(999999)
    internal val søknadId: UUID = UUID.fromString("8707a819-71de-43e4-9b6e-c9a912bb1f70")
    internal val behandlingId: UUID = UUID.fromString("a602aa68-c989-43e3-9fb7-cb488a2a3821")
    internal val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlinger")
    internal val attestant = NavIdentBruker.Attestant("attestant")
    internal val søknadJournalpostId = JournalpostId("søknadJournalpostId")
    internal val søknadOppgaveId = OppgaveId("søknadOppgaveId")
    internal val fnr = FnrGenerator.random()

    internal val person = Person(
        ident = Ident(
            fnr = fnr,
            aktørId = AktørId(aktørId = "123")
        ),
        navn = Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy")
    )

    internal fun createOpprettetBehandling(): Behandling {
        return behandlingFactory.createBehandling(
            id = behandlingId,
            søknad = Søknad.Journalført.MedOppgave(
                id = søknadId,
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = søknadOppgaveId,
                journalpostId = søknadJournalpostId
            ),
            status = Behandling.BehandlingsStatus.OPPRETTET,
            saksbehandler = saksbehandler,
            attestering = Attestering.Iverksatt(attestant),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            oppgaveId = søknadOppgaveId,
        )
    }

    internal val behandlingsinformasjon = Behandlingsinformasjon(
        uførhet = Uførhet(
            status = VilkårOppfylt,
            uføregrad = 20,
            forventetInntekt = 10,
            begrunnelse = null
        ),
        flyktning = Flyktning(
            status = Status.VilkårOppfylt,
            begrunnelse = null
        ),
        lovligOpphold = LovligOpphold(
            status = LovligOpphold.Status.VilkårOppfylt,
            begrunnelse = null
        ),
        fastOppholdINorge = FastOppholdINorge(
            status = FastOppholdINorge.Status.VilkårOppfylt,
            begrunnelse = null
        ),
        institusjonsopphold = Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårOppfylt,
            begrunnelse = null
        ),
        oppholdIUtlandet = OppholdIUtlandet(
            status = SkalHoldeSegINorge,
            begrunnelse = null
        ),
        formue = Formue(
            status = Formue.Status.VilkårOppfylt,
            borSøkerMedEPS = true,
            verdier = Verdier(
                verdiIkkePrimærbolig = 0,
                verdiEiendommer = 0,
                verdiKjøretøy = 0,
                innskudd = 0,
                verdipapir = 0,
                pengerSkyldt = 0,
                kontanter = 0,
                depositumskonto = 0
            ),
            epsVerdier = Verdier(
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
        personligOppmøte = PersonligOppmøte(
            status = MøttPersonlig,
            begrunnelse = null
        ),
        bosituasjon = Bosituasjon(
            epsAlder = null,
            delerBolig = false,
            ektemakeEllerSamboerUførFlyktning = false,
            begrunnelse = null
        ),
        ektefelle = Ektefelle(
            fnr = Fnr("17087524256"),
            navn = Navn("fornavn", null, "etternavn"),
            kjønn = null,
            fødselsdato = null,
            adressebeskyttelse = null,
            skjermet = null
        )
    )

    internal fun createService(
        behandlingRepo: BehandlingRepo = mock(),
        hendelsesloggRepo: HendelsesloggRepo = mock(),
        utbetalingService: UtbetalingService = mock(),
        oppgaveService: OppgaveService = mock(),
        søknadService: SøknadService = mock(),
        søknadRepo: SøknadRepo = mock(),
        personService: PersonService = mock(),
        brevService: BrevService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        microsoftGraphApiOppslag: MicrosoftGraphApiOppslag = microsoftGraphMock.oppslagMock,
        iverksettBehandlingService: IverksettBehandlingService = mock(),
        observer: EventObserver = observerMock,
    ) = BehandlingServiceImpl(
        behandlingRepo = behandlingRepo,
        hendelsesloggRepo = hendelsesloggRepo,
        utbetalingService = utbetalingService,
        oppgaveService = oppgaveService,
        søknadService = søknadService,
        søknadRepo = søknadRepo,
        personService = personService,
        brevService = brevService,
        behandlingMetrics = behandlingMetrics,
        clock = fixedClock,
        microsoftGraphApiClient = microsoftGraphApiOppslag,
        iverksettBehandlingService = iverksettBehandlingService,
    ).apply { addObserver(observer) }

    internal object microsoftGraphMock {
        val response = MicrosoftGraphResponse(
            onPremisesSamAccountName = "",
            displayName = "Nav Navesen",
            givenName = "",
            mail = "",
            officeLocation = "",
            surname = "",
            userPrincipalName = "",
            id = "",
            jobTitle = ""
        )

        val oppslagMock: MicrosoftGraphApiOppslag by lazy {
            mock {
                on { hentBrukerinformasjon(any()) } doReturn Either.right(response)
                on { hentBrukerinformasjonForNavIdent(any()) } doReturn Either.right(response)
            }
        }
    }
}
