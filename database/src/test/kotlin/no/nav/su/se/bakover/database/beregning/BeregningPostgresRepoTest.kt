package no.nav.su.se.bakover.database.beregning

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.antall
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test
import java.util.UUID

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

            val beregning = BeregningFactory.persistert(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                periode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
                sats = Sats.HØY,
                fradrag = listOf(
                    FradragFactory.persistert(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        type = Fradragstype.Arbeidsinntekt,
                        beløp = 54321.1234,
                        periode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
                        utenlandskInntekt = null
                    )
                )
            )

            repo.opprettBeregningForBehandling(nySøknadsbehandling.id, beregning)

            val hentet = repo.hentBeregningForBehandling(nySøknadsbehandling.id)!!

            hentet shouldBe beregning
        }
    }

    @Test
    fun `slett beregning`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val nySøknadsbehandling = testDataHelper.insertBehandling(sak.id, søknad)

            repo.opprettBeregningForBehandling(
                behandlingId = nySøknadsbehandling.id,
                beregning = BeregningFactory.ny(
                    periode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 31.januar(2020)),
                    sats = Sats.HØY,
                    fradrag = emptyList()
                )
            )

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
                BeregningFactory.ny(
                    periode = Periode(1.januar(2020), 31.desember(2020)),
                    sats = Sats.HØY,
                    fradrag = listOf(
                        FradragFactory.ny(
                            periode = Periode(1.januar(2020), 31.desember(2020)),
                            type = Fradragstype.Arbeidsinntekt,
                            beløp = 10000.0,
                            utenlandskInntekt = null
                        )
                    )
                )
            )

            selectCount(from = "beregning", where = "behandlingId", id = nySøknadsbehandling.id.toString()) shouldBe 1
            selectCount(from = "beregning", where = "id", id = gammelBeregning.id().toString()) shouldBe 1
            selectCount(from = "fradrag", where = "beregningId", id = gammelBeregning.id().toString()) shouldBe 1

            val nyBeregning = BeregningFactory.ny(
                periode = Periode(1.januar(2020), 31.desember(2020)),
                sats = Sats.HØY,
                fradrag = emptyList()
            )
            repo.opprettBeregningForBehandling(nySøknadsbehandling.id, nyBeregning)

            selectCount(from = "beregning", where = "behandlingId", id = nySøknadsbehandling.id.toString()) shouldBe 1

            selectCount(from = "beregning", where = "id", id = nyBeregning.id().toString()) shouldBe 1
            selectCount(from = "fradrag", where = "beregningId", id = nyBeregning.id().toString()) shouldBe 0

            selectCount(from = "beregning", where = "id", id = gammelBeregning.id().toString()) shouldBe 0
            selectCount(from = "fradrag", where = "beregningId", id = gammelBeregning.id().toString()) shouldBe 0
        }
    }

    private fun selectCount(from: String, where: String, id: String) =
        EmbeddedDatabase.instance().withSession { session ->
            "select count(*) from $from where $where='$id'".antall(session = session)
        }
}
