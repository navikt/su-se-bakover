package no.nav.su.se.bakover.database.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nyReguleringssupplement
import no.nav.su.se.bakover.test.nyReguleringssupplementFor
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
class ReguleringssupplementPostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `lagrer tom supplement`() {
        val testDataHelper = TestDataHelper(dataSource)
        testDataHelper.reguleringRepo.lagre(Reguleringssupplement.empty(fixedClock))
        testDataHelper.sessionFactory.withSession {
            """select * from reguleringssupplement""".hent(emptyMap(), it) {
                val rowSupplement = it.string("supplement")
                rowSupplement shouldBe "[]"
            }
        }
    }

    @Test
    fun `lagrer supplement med innhold`() {
        val testDataHelper = TestDataHelper(dataSource)
        val id = UUID.randomUUID()
        val fnr = Fnr.generer()
        val supplementFor = nyReguleringssupplementFor(fnr)
        testDataHelper.reguleringRepo.lagre(
            nyReguleringssupplement(
                id = id,
                opprettet = fixedTidspunkt,
                originalCsv = "Okei",
                supplementFor,
            ),
        )
        testDataHelper.sessionFactory.withSession {
            """select * from reguleringssupplement""".hent(emptyMap(), it) {
                it.uuid("id") shouldBe id
                it.string("supplement") shouldBe """[{"fnr": "$fnr", "perType": [{"vedtak": [{"type": "endring", "beløp": 1000, "fradrag": [{"beløp": 1000, "fraOgMed": "2021-04-01", "tilOgMed": "2021-04-30", "eksterndata": {"fnr": "11111111111", "fraOgMed": "01.05.2021", "sakstype": "UFOREP", "tilOgMed": null, "nettoYtelse": "11000", "vedtakstype": "REGULERING", "bruttoYtelse": "10000", "ytelseskomponenttype": "ST", "nettoYtelseskomponent": "11000", "bruttoYtelseskomponent": "10000"}, "vedtakstype": "Endring"}], "periode": {"fraOgMed": "2021-04-01", "tilOgMed": "2021-04-30"}}, {"type": "regulering", "beløp": 1000, "fradrag": [{"beløp": 1000, "fraOgMed": "2021-05-01", "tilOgMed": null, "eksterndata": {"fnr": "11111111111", "fraOgMed": "01.05.2021", "sakstype": "UFOREP", "tilOgMed": null, "nettoYtelse": "11000", "vedtakstype": "REGULERING", "bruttoYtelse": "10000", "ytelseskomponenttype": "ST", "nettoYtelseskomponent": "11000", "bruttoYtelseskomponent": "10000"}, "vedtakstype": "Regulering"}], "periodeOptionalTilOgMed": {"fraOgMed": "2021-05-01", "tilOgMed": null}}], "fradragskategori": "Alderspensjon"}]}]""".trimIndent()
                it.string("csv") shouldBe "Okei"
            }
        }
    }
}
