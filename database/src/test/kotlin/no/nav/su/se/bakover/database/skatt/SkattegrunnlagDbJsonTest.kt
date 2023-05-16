package no.nav.su.se.bakover.database.skatt

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.skatt.SkattegrunnlagDbJson.Companion.toDbJson
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.skatt.nySamletSkattegrunnlagForÅrOgStadieOppgjør
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import org.junit.jupiter.api.Test
import java.util.UUID

class SkattegrunnlagDbJsonTest {

    @Test
    fun `kan serialisere og deserialisere skattegrunnlag`() {
        val id = UUID.randomUUID()
        val expected = nySkattegrunnlag(id = id, årsgrunnlag = nonEmptyListOf(nySamletSkattegrunnlagForÅrOgStadieOppgjør()))
        SkattegrunnlagDbJson.toSkattegrunnlag(
            id = id,
            årsgrunnlagJson = expected.toDbJson(),
            fnr = fnr.toString(),
            hentetTidspunkt = fixedTidspunkt,
            saksbehandler = saksbehandler.toString(),
            //language=json
            årSpurtFor = """{"fra": "2021", "til": "2021"}""",
        ) shouldBe expected
    }
}
