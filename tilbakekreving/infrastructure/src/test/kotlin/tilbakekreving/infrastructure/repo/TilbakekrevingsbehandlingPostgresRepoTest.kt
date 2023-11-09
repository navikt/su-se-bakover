package tilbakekreving.infrastructure.repo

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

class TilbakekrevingsbehandlingPostgresRepoTest {

    @Test
    fun `henter åpne tilbakekrevingsbehandling`() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)
            val opprettet = testDataHelper.persisterOpprettetTilbakekrevingsbehandlingHendelse()
            testDataHelper.persisterAvbruttTilbakekrevingsbehandlingHendelse()

            testDataHelper.tilbakekrevingHendelseRepo.hentÅpneBehandlingssammendrag().let {
                it.size shouldBe 1
                it.first().status shouldBe Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING
                it.first().behandlingstype shouldBe Behandlingssammendrag.Behandlingstype.TILBAKEKREVING
                it.first().behandlingStartet shouldBe opprettet.seventh.hendelsestidspunkt
            }
        }
    }

    @Test
    fun `henter ferdige tilbakekrevingsbehandling`() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)
            testDataHelper.persisterOpprettetTilbakekrevingsbehandlingHendelse()
            testDataHelper.persisterAvbruttTilbakekrevingsbehandlingHendelse()

            testDataHelper.tilbakekrevingHendelseRepo.hentFerdigeBehandlingssamendrag().let {
                it.size shouldBe 1
                it.first().status shouldBe Behandlingssammendrag.Behandlingsstatus.AVSLAG
                it.first().behandlingstype shouldBe Behandlingssammendrag.Behandlingstype.TILBAKEKREVING
                it.first().behandlingStartet shouldBe fixedTidspunkt
            }
        }
    }
}
