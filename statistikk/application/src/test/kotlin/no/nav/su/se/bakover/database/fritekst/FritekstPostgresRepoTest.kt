package no.nav.su.se.bakover.database.fritekst

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.fritekst.Fritekst
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FritekstPostgresRepoTest {

    @Test
    fun `lagre, endre, hente og tømme fritekst`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val fritekstRepo = testDataHelper.fritekstRepo

            val fritekst = Fritekst(
                referanseId = UUID.randomUUID(),
                type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING,
                fritekst = "HUBBA BUBBA!",
            )
            fritekstRepo.lagreFritekst(fritekst)
            fritekstRepo.hentFritekst(fritekst.referanseId, fritekst.type) shouldBe fritekst

            val fritekstEndret = fritekst.copy(
                fritekst = "BOBBA FETT!",
            )
            fritekstRepo.lagreFritekst(fritekstEndret)
            fritekstRepo.hentFritekst(fritekst.referanseId, fritekst.type) shouldBe fritekstEndret

            fritekstRepo.tømFritekst(fritekst.referanseId, fritekst.type)
            fritekstRepo.hentFritekst(fritekst.referanseId, fritekst.type) shouldBe null
        }
    }
}
