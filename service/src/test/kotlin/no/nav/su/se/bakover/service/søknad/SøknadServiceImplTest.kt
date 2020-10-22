package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.LukketSøknadBrevinnhold
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class SøknadServiceImplTest {
    private val fixedClock = Clock.fixed(1.januar(2020).plusDays(9).startOfDay().instant, ZoneOffset.UTC)

    private val sakId = UUID.randomUUID()
    private val fnr = Fnr("12345678910")
    private val sak = Sak(
        id = sakId,
        opprettet = Tidspunkt.now(),
        fnr = fnr,
        søknader = mutableListOf(),
        behandlinger = mutableListOf(),
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            utbetalinger = emptyList()
        )
    )

    private val person = Person(
        ident = Ident(fnr = fnr, aktørId = AktørId(aktørId = "1234")),
        navn = Person.Navn(fornavn = "navn", mellomnavn = null, etternavn = "navnesen"),
        telefonnummer = null,
        adresse = null,
        statsborgerskap = null,
        kjønn = null,
        adressebeskyttelse = null,
        skjermet = null
    )

    private val lukketSøknad = Søknad.Lukket.Trukket(
        tidspunkt = Tidspunkt.now(),
        saksbehandler = Saksbehandler(navIdent = "12345"),
        datoSøkerTrakkSøknad = LocalDate.now()
    )

    private val søknad = Søknad(
        sakId = sakId,
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        søknadInnhold = SøknadInnholdTestdataBuilder.build(),
        lukket = null
    )

    private val saksbehandler = Saksbehandler("Z993156")

    @Test
    fun `trekker en søknad`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on {
                lukkSøknad(
                    søknad.id,
                    Søknad.Lukket.Trukket(
                        tidspunkt = now(),
                        saksbehandler = saksbehandler,
                        datoSøkerTrakkSøknad = LocalDate.now()
                    )
                )
            }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(fnr) } doReturn person.right()
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn "journalpostId".right()
            on { distribuerBrev(any()) } doReturn "bestillingsId".right()
        }

        createService(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            personOppslag = personOppslagMock
        ).lukkSøknad(
            søknadId = søknad.id,
            lukketSøknad = lukketSøknad
        ) shouldBe sak.right()

        verify(søknadRepoMock).hentSøknad(søknad.id)
        verify(søknadRepoMock).harSøknadPåbegyntBehandling(søknad.id)
        verify(personOppslagMock).person(fnr)
        verify(brevServiceMock).journalførBrev(
            LukketSøknadBrevinnhold.lagLukketSøknadBrevinnhold(
                person,
                søknad,
                lukketSøknad
            ),
            sak.id
        )
        verify(brevServiceMock).distribuerBrev("journalpostId")
        verify(søknadRepoMock).lukkSøknad(søknad.id, lukketSøknad)
        verify(sakServiceMock).hentSak(sak.id)
    }

    @Test
    fun `en søknad med behandling skal ikke bli trukket`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn true
        }
        createService(
            søknadRepo = søknadRepoMock
        ).lukkSøknad(
            søknadId = søknad.id,
            lukketSøknad = lukketSøknad
        ) shouldBe KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()
    }

    @Test
    fun `en allerede trukket søknad skal ikke bli trukket`() {
        val saksbehandler = Saksbehandler("Z993156")
        val søknad = søknad.copy(
            lukket = Søknad.Lukket.Trukket(
                tidspunkt = Tidspunkt.now(),
                saksbehandler = saksbehandler,
                datoSøkerTrakkSøknad = LocalDate.now()
            )
        )
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
        }
        createService(
            søknadRepo = søknadRepoMock
        ).lukkSøknad(
            søknadId = søknad.id,
            lukketSøknad = lukketSøknad
        ) shouldBe KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
    }

    @Test
    fun `generer et brevutkast for en lukket søknad`() {
        val saksbehandler = Saksbehandler("Z993156")
        val søknad = søknad.copy(
            lukket = Søknad.Lukket.Trukket(
                tidspunkt = Tidspunkt.now(),
                saksbehandler = saksbehandler,
                datoSøkerTrakkSøknad = LocalDate.now()
            )
        )
        val brevPdf = "some-pdf-document".toByteArray()

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
        }
        val brevServiceMock = mock<BrevService> {
            on {
                lagBrev(
                    LukketSøknadBrevinnhold.TrukketSøknadBrevinnhold.lagTrukketSøknadBrevinnhold(
                        søknad = søknad,
                        person = person,
                        lukketSøknad = lukketSøknad
                    )
                )
            } doReturn brevPdf.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(fnr) } doReturn person.right()
        }
        createService(
            søknadRepo = søknadRepoMock,
            personOppslag = personOppslagMock,
            brevService = brevServiceMock
        ).lagLukketSøknadBrevutkast(
            søknadId = søknad.id,
            lukketSøknad = lukketSøknad
        ) shouldBe brevPdf.right()
    }

    @Test
    fun `får ikke brevutkast hvis brevService returnere feil`() {
        val søknad = søknad.copy(
            lukket = Søknad.Lukket.Trukket(
                tidspunkt = Tidspunkt.now(),
                saksbehandler = saksbehandler,
                datoSøkerTrakkSøknad = LocalDate.now()
            )
        )

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
        }
        val brevServiceMock = mock<BrevService> {
            on {
                lagBrev(
                    LukketSøknadBrevinnhold.TrukketSøknadBrevinnhold.lagTrukketSøknadBrevinnhold(
                        søknad = søknad,
                        person = person,
                        lukketSøknad = lukketSøknad
                    )
                )
            } doReturn KunneIkkeLageBrev.KunneIkkeGenererePdf.left()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(fnr) } doReturn person.right()
        }
        createService(
            søknadRepo = søknadRepoMock,
            personOppslag = personOppslagMock,
            brevService = brevServiceMock
        ).lagLukketSøknadBrevutkast(
            søknadId = søknad.id,
            lukketSøknad = lukketSøknad
        ) shouldBe KunneIkkeLageBrevutkast.FeilVedGenereringAvBrevutkast.left()
    }

    private fun createService(
        søknadRepo: SøknadRepo = mock(),
        sakService: SakService = mock(),
        sakFactory: SakFactory = mock(),
        personOppslag: PersonOppslag = mock(),
        oppgaveClient: OppgaveClient = mock(),
        brevService: BrevService = mock()
    ) = SøknadServiceImpl(
        søknadRepo = søknadRepo,
        sakService = sakService,
        sakFactory = sakFactory,
        personOppslag = personOppslag,
        oppgaveClient = oppgaveClient,
        brevService = brevService,
    )
}
