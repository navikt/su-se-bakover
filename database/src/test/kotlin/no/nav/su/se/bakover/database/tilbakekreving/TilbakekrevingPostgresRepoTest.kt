package no.nav.su.se.bakover.database.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsavgjørelse
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Assertions.fail
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
                clock = fixedClock
            ).also { repo.lagreKravgrunnlag(it) }

            RåttKravgrunnlag.ny(
                melding = "Ferdigbehandlet",
                clock = fixedClock
            ).tilFerdigbehandlet().also { repo.lagreKravgrunnlag(it) }
            repo.hentUbehandlaKravgrunnlag() shouldBe listOf(
                nyttKravgrunnlag
            )
        }
    }

    @Test
    fun `kan lagre og hente ferdigbehandla kravgrunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.tilbakekrevingRepo
            val ferdigbehandletKravgrunnlag = RåttKravgrunnlag.ny(
                melding = "someMelding",
                clock = fixedClock
            ).tilFerdigbehandlet()
            repo.lagreKravgrunnlag(ferdigbehandletKravgrunnlag)
            repo.hentUbehandlaKravgrunnlag() shouldBe emptyList()
            fail("validate that the db contains this kravgrunnlag")
        }
    }

    @Test
    fun `kan lagre og hente ikke-oversendt tilbakekrevingsavgjørelse som skal føre til tilbakekreving`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.tilbakekrevingRepo
            val ferdigbehandletKravgrunnlag = Tilbakekrevingsavgjørelse.SkalTilbakekreve.Forsto(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = UUID.randomUUID(),
                revurderingId = UUID.randomUUID(),
                periode = Periode.create(fraOgMed = 1.mai(2021), tilOgMed = 31.juli(2021)),
                oversendtTidspunkt = null
            ).tilFerdigbehandlet()
            repo.lagreTilbakekrevingsavgjørelse(ferdigbehandletKravgrunnlag)
            repo.hentUbehandlaKravgrunnlag() shouldBe emptyList()
            fail("validate that the db contains this kravgrunnlag")
        }
    }
}
