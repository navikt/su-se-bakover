package no.nav.su.se.bakover.kontrollsamtale.infrastructure.persistence

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleHandling
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleHendelse
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtaler
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.kontrollsamtale.planlagtKontrollsamtale
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class KontrollsamtalePostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `lagrer og henter kontrollsamtale med hendelser`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = KontrollsamtalePostgresRepo(testDataHelper.sessionFactory, testDataHelper.dbMetrics)
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val opprettet = Tidspunkt.now(testDataHelper.clock)
        val annullertTidspunkt = opprettet.plusUnits(1)

        val kontrollsamtale = planlagtKontrollsamtale(
            opprettet = opprettet,
            sakId = sak.id,
        ).copy(
            hendelser = listOf(
                KontrollsamtaleHendelse(
                    navIdent = NavIdentBruker.Saksbehandler("Z123456"),
                    tidspunkt = opprettet,
                    handling = KontrollsamtaleHandling.PLANLAGT_INNKALLING,
                ),
            ),
        )

        repo.lagre(kontrollsamtale, null)

        val annullert = kontrollsamtale.annuller().getOrFail().copy(
            hendelser = kontrollsamtale.hendelser + KontrollsamtaleHendelse(
                navIdent = NavIdentBruker.Saksbehandler.systembruker(),
                tidspunkt = annullertTidspunkt,
                handling = KontrollsamtaleHandling.ANNULLERT,
            ),
        )

        repo.lagre(annullert, null)

        val hentetForSak = repo.hentForSakId(sak.id, null)
        hentetForSak shouldBe Kontrollsamtaler(sak.id, listOf(annullert))
        hentetForSak.single().hendelser shouldBe annullert.hendelser
        hentetForSak.single().hendelser.last().handling shouldBe KontrollsamtaleHandling.ANNULLERT

        val hentetForKontrollsamtaleId = repo.hentForKontrollsamtaleId(annullert.id, null)
        hentetForKontrollsamtaleId shouldBe annullert
        hentetForKontrollsamtaleId!!.hendelser shouldBe annullert.hendelser
        hentetForKontrollsamtaleId.hendelser.last().handling shouldBe KontrollsamtaleHandling.ANNULLERT
    }
}
