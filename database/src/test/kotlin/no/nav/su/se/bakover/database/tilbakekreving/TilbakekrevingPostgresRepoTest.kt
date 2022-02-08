package no.nav.su.se.bakover.database.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.UUID

internal class TilbakekrevingPostgresRepoTest {

    @Test
    fun `kan lagre og hente ubehandla kravgrunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.tilbakekrevingRepo

            val nyttKravgrunnlag = RåttKravgrunnlag.ny(
                melding = "Ubehandlet",
                clock = fixedClock,
            ).also { repo.lagreKravgrunnlag(it) }

            RåttKravgrunnlag.ny(
                melding = "Ferdigbehandlet",
                clock = fixedClock,
            ).tilFerdigbehandlet().also { repo.lagreKravgrunnlag(it) }
            repo.hentUbehandlaKravgrunnlag() shouldBe listOf(
                nyttKravgrunnlag,
            )
        }
    }

    @Test
    @Disabled
    fun `kan lagre og hente ferdigbehandla kravgrunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.tilbakekrevingRepo
            val ferdigbehandletKravgrunnlag = RåttKravgrunnlag.ny(
                melding = "someMelding",
                clock = fixedClock,
            ).tilFerdigbehandlet()
            repo.lagreKravgrunnlag(ferdigbehandletKravgrunnlag)
            repo.hentUbehandlaKravgrunnlag() shouldBe emptyList()
            fail("validate that the db contains this kravgrunnlag")
        }
    }

    @Test
    fun `kan lagre og hente uten behov for tilbakekrevingsbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val revurdering = testDataHelper.simulertInnvilgetRevurdering()

            (testDataHelper.revurderingRepo.hent(revurdering.id) as SimulertRevurdering).tilbakekrevingsbehandling shouldBe Tilbakekrevingsbehandling.IkkeBehovForTilbakekreving
        }
    }

    @Test
    fun `kan lagre og hente tilbakekrevingsbehandlinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val revurdering = testDataHelper.simulertInnvilgetRevurdering()

            val ikkeAvgjort = Tilbakekrevingsbehandling.VurderTilbakekreving.IkkeAvgjort(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = revurdering.sakId,
                revurderingId = revurdering.id,
                periode = revurdering.periode,
            )

            testDataHelper.revurderingRepo.lagre(revurdering.copy(tilbakekrevingsbehandling = ikkeAvgjort))
            (testDataHelper.revurderingRepo.hent(revurdering.id) as SimulertRevurdering).tilbakekrevingsbehandling shouldBe ikkeAvgjort

            val forsto = ikkeAvgjort.forsto()
            testDataHelper.revurderingRepo.lagre(revurdering.copy(tilbakekrevingsbehandling = forsto))
            (testDataHelper.revurderingRepo.hent(revurdering.id) as SimulertRevurdering).tilbakekrevingsbehandling shouldBe forsto

            val burdeForstått = ikkeAvgjort.burdeForstått()
            testDataHelper.revurderingRepo.lagre(revurdering.copy(tilbakekrevingsbehandling = burdeForstått))
            (testDataHelper.revurderingRepo.hent(revurdering.id) as SimulertRevurdering).tilbakekrevingsbehandling shouldBe burdeForstått

            val kunneIkkeForstå = ikkeAvgjort.kunneIkkeForstå()
            testDataHelper.revurderingRepo.lagre(revurdering.copy(tilbakekrevingsbehandling = kunneIkkeForstå))
            (testDataHelper.revurderingRepo.hent(revurdering.id) as SimulertRevurdering).tilbakekrevingsbehandling shouldBe kunneIkkeForstå
        }
    }
}
