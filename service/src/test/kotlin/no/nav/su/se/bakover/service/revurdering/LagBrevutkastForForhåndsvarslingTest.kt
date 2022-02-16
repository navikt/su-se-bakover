package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.bruker.IdentClient
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

class LagBrevutkastForForhåndsvarslingTest {

    @Test
    fun `lag brevutkast for forhåndsvarsling feiler dersom revurderingen ikke finnes`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn null
        }

        RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).lagBrevutkastForForhåndsvarsling(
            UUID.randomUUID(),
            "fritekst til forhåndsvarsling",
        ) shouldBe KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering.left()
    }

    @Test
    fun `lag brevutkast for forhåndsvarsling feiler dersom vi ikke finner personen knyttet til revurderingen`() {
        val simulertRevurdering = RevurderingTestUtils.simulertRevurderingInnvilget

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
        ).lagBrevutkastForForhåndsvarsling(
            UUID.randomUUID(),
            "fritekst til forhåndsvarsling",
        ) shouldBe KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson.left()
    }

    @Test
    fun `lag brevutkast for forhåndsvarsling feiler dersom vi klarer lage brevet`() {
        val simulertRevurdering = RevurderingTestUtils.simulertRevurderingInnvilget
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn BehandlingTestUtils.person.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }

        val microsoftGraphApiClientMock = mock<IdentClient> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
        }

        RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            brevService = brevServiceMock,
            identClient = microsoftGraphApiClientMock,
        ).lagBrevutkastForForhåndsvarsling(
            UUID.randomUUID(),
            "fritekst til forhåndsvarsling",
        ) shouldBe KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast.left()
    }
}
