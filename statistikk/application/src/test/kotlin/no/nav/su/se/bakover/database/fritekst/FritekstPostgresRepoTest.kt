package no.nav.su.se.bakover.database.fritekst

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.fritekst.Fritekst
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class FritekstPostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `lagre, endre, hente og tømme fritekst`() {
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

        fritekstRepo.slettFritekst(fritekst.referanseId, fritekst.type)
        fritekstRepo.hentFritekst(fritekst.referanseId, fritekst.type) shouldBe null
    }

    @Test
    fun `lagre flere av samme type`() {
        val testDataHelper = TestDataHelper(dataSource)
        val fritekstRepo = testDataHelper.fritekstRepo

        val fritekst = Fritekst(
            referanseId = UUID.randomUUID(),
            type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING,
            fritekst = "HUBBA BUBBA!",
        )
        fritekstRepo.lagreFritekst(fritekst)
        fritekstRepo.hentFritekst(fritekst.referanseId, fritekst.type) shouldBe fritekst

        val fritekstTo = Fritekst(
            referanseId = UUID.randomUUID(),
            type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING,
            fritekst = "HUBBA BUBBA!",
        )
        fritekstRepo.lagreFritekst(fritekstTo)
        fritekstRepo.hentFritekst(fritekstTo.referanseId, fritekstTo.type) shouldBe fritekstTo

        fritekstRepo.slettFritekst(fritekstTo.referanseId, fritekstTo.type)
        fritekstRepo.hentFritekst(fritekstTo.referanseId, fritekstTo.type) shouldBe null
    }
}
