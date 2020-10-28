package no.nav.su.se.bakover.database.beregning

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.antall
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.domain.beregning.Sats
import org.junit.jupiter.api.Test

internal class BeregningPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = BeregningPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `hent beregning`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val nySøknadsbehandling = testDataHelper.insertBehandling(sak.id, søknad)
            val beregning = testDataHelper.insertBeregning(nySøknadsbehandling.id)

            val hentet = repo.hentBeregningForBehandling(nySøknadsbehandling.id)

            hentet shouldBe beregning
        }
    }

    @Test
    fun `slett beregning`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val nySøknadsbehandling = testDataHelper.insertBehandling(sak.id, søknad)
            testDataHelper.insertBeregning(nySøknadsbehandling.id)

            val before = repo.hentBeregningForBehandling(nySøknadsbehandling.id)
            repo.slettBeregningForBehandling(nySøknadsbehandling.id)

            val after = repo.hentBeregningForBehandling(nySøknadsbehandling.id)
            before shouldNotBe null
            after shouldBe null
        }
    }

    @Test
    fun `sletter eksisterende beregninger når nye opprettes`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val nySøknadsbehandling = testDataHelper.insertBehandling(sak.id, søknad)
            val gammelBeregning = repo.opprettBeregningForBehandling(
                nySøknadsbehandling.id,
                Beregning(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    sats = Sats.HØY,
                    fradrag = listOf(
                        Fradrag(
                            type = Fradragstype.Arbeidsinntekt,
                            beløp = 10000,
                            utenlandskInntekt = null,
                            inntektDelerAvPeriode = null
                        )
                    )
                )
            )

            selectCount(from = "beregning", where = "behandlingId", id = nySøknadsbehandling.id.toString()) shouldBe 1
            selectCount(from = "beregning", where = "id", id = gammelBeregning.id.toString()) shouldBe 1
            selectCount(from = "månedsberegning", where = "beregningId", id = gammelBeregning.id.toString()) shouldBe 12
            selectCount(from = "fradrag", where = "beregningId", id = gammelBeregning.id.toString()) shouldBe 1

            val nyBeregning = Beregning(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.desember(2020),
                sats = Sats.HØY,
                fradrag = emptyList()
            )
            repo.opprettBeregningForBehandling(nySøknadsbehandling.id, nyBeregning)

            selectCount(from = "beregning", where = "behandlingId", id = nySøknadsbehandling.id.toString()) shouldBe 1

            selectCount(from = "beregning", where = "id", id = nyBeregning.id.toString()) shouldBe 1
            selectCount(from = "månedsberegning", where = "beregningId", id = nyBeregning.id.toString()) shouldBe 12
            selectCount(from = "fradrag", where = "beregningId", id = nyBeregning.id.toString()) shouldBe 0

            selectCount(from = "beregning", where = "id", id = gammelBeregning.id.toString()) shouldBe 0
            selectCount(from = "månedsberegning", where = "beregningId", id = gammelBeregning.id.toString()) shouldBe 0
            selectCount(from = "fradrag", where = "beregningId", id = gammelBeregning.id.toString()) shouldBe 0
        }
    }

    private fun selectCount(from: String, where: String, id: String) =
        EmbeddedDatabase.instance().withSession { session ->
            "select count(*) from $from where $where='$id'".antall(session = session)
        }
}
