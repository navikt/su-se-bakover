package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person.Navn
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
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
import java.time.Clock
import java.time.ZoneOffset

object BehandlingTestUtils {

    internal val tidspunkt = 15.juni(2020).atStartOfDay().toTidspunkt(ZoneOffset.UTC)

    private val fixedClock = Clock.fixed(15.juni(2020).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val observerMock: EventObserver = mock { on { handle(any()) }.doNothing() }

    internal fun createService(
        behandlingRepo: BehandlingRepo = mock(),
        hendelsesloggRepo: HendelsesloggRepo = mock(),
        utbetalingService: UtbetalingService = mock(),
        oppgaveService: OppgaveService = mock(),
        søknadService: SøknadService = mock(),
        søknadRepo: SøknadRepo = mock(),
        personService: PersonService = mock(),
        brevService: BrevService = mock(),
        opprettVedtakssnapshotService: OpprettVedtakssnapshotService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        microsoftGraphApiOppslag: MicrosoftGraphApiOppslag,
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
        opprettVedtakssnapshotService = opprettVedtakssnapshotService,
        behandlingMetrics = behandlingMetrics,
        clock = fixedClock,
        microsoftGraphApiClient = microsoftGraphApiOppslag
    ).apply { observers.add(observer) }

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
            epsFnr = null,
            delerBolig = false,
            ektemakeEllerSamboerUførFlyktning = false,
            begrunnelse = null
        ),
        ektefelle = Ektefelle(
            fnr = Fnr("17087524256"),
            navn = Navn("fornavn", null, "etternavn"),
            kjønn = null,
            adressebeskyttelse = null,
            skjermet = null
        )
    )

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

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjon(any()) } doReturn Either.right(response)
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn Either.right(response)
        }
    }
}
