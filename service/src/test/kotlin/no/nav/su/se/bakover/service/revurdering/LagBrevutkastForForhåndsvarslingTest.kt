package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertRevurdering
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class LagBrevutkastForForhåndsvarslingTest {

    @Test
    fun `lager brevutkast for forhåndsvarsel - saksbehandler som lager utkastet, er ikke den som har gjort behandlingen`() {
        val simulertRevurdering = simulertRevurdering().second
        val saksbehandlerSomLagerBrev = NavIdentBruker.Saksbehandler("saksbehandlerSomLagerBrevet")
        val identClientMock = mock<IdentClient> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandlerSomLagerBrev.navIdent.right()
        }
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            personService = mock {
                on { hentPerson(any()) } doReturn person(fnr, aktørId).right()
            },
            brevService = mock {
                on { lagBrev(any()) } doReturn "".toByteArray().right()
            },
            identClient = identClientMock,
        ).let {
            simulertRevurdering.saksbehandler shouldNotBe saksbehandlerSomLagerBrev
            it.revurderingService.lagBrevutkastForForhåndsvarsling(
                simulertRevurdering.id,
                saksbehandlerSomLagerBrev,
                "saksbehandler og saksbehandler som lager brev er ikke de samme",
            ).shouldBeRight()
        }

        verify(identClientMock).hentNavnForNavIdent(argThat { it shouldBe saksbehandlerSomLagerBrev })
    }

    @Test
    fun `lag brevutkast for forhåndsvarsling feiler dersom revurderingen ikke finnes`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn null
            },
        ).let {
            it.revurderingService.lagBrevutkastForForhåndsvarsling(
                UUID.randomUUID(),
                saksbehandler,
                "fritekst til forhåndsvarsling",
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering.left()
        }
    }

    @Test
    fun `lag brevutkast for forhåndsvarsling feiler dersom vi ikke finner personen knyttet til revurderingen`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering().second
            },
            personService = mock {
                on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
            },
        ).let {
            it.revurderingService.lagBrevutkastForForhåndsvarsling(
                UUID.randomUUID(),
                saksbehandler,
                "fritekst til forhåndsvarsling",
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson.left()
        }
    }

    @Test
    fun `lag brevutkast for forhåndsvarsling feiler dersom vi ikke klarer lage brevet`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering().second
            },
            personService = mock {
                on { hentPerson(any()) } doReturn person(fnr, aktørId).right()
            },
            brevService = mock {
                on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
            },
        ).let {
            it.revurderingService.lagBrevutkastForForhåndsvarsling(
                UUID.randomUUID(),
                saksbehandler,
                "fritekst til forhåndsvarsling",
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast.left()
        }
    }
}
