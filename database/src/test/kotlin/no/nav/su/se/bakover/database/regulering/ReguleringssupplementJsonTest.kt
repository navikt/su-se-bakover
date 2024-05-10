package no.nav.su.se.bakover.database.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.epsFnr
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.nyEksternSupplementRegulering
import no.nav.su.se.bakover.test.nyReguleringssupplementFor
import no.nav.su.se.bakover.test.nyReguleringssupplementInnholdPerType
import org.junit.jupiter.api.Test
import java.util.UUID

class ReguleringssupplementJsonTest {
    private val ersId = UUID.randomUUID()
    private val ers = nyEksternSupplementRegulering(id = ersId)
    private val ersMedNull = nyEksternSupplementRegulering(id = null)
    private val ersMedInnholdId = UUID.randomUUID()

    private val ersMedInnhold = nyEksternSupplementRegulering(
        id = ersMedInnholdId,
        bruker = nyReguleringssupplementFor(fnr = fnr, nyReguleringssupplementInnholdPerType()),
        eps = listOf(nyReguleringssupplementFor(fnr = epsFnr, nyReguleringssupplementInnholdPerType())),
    )

    @Test
    fun `serialiserer og deserialiserer`() {
        ers.toDbJson() shouldBe """{"supplementId":"$ersId","bruker":null,"eps":[]}"""
        ersMedNull.toDbJson() shouldBe """{"supplementId":null,"bruker":null,"eps":[]}"""

        //language=json
        ersMedInnhold.toDbJson() shouldBe """{"supplementId":"$ersMedInnholdId","bruker":{"fnr":"$fnr","perType":[{"vedtak":[{"type":"endring","måned":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"},"fradrag":[{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30","vedtakstype":"Endring","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000},{"type":"regulering","periodeOptionalTilOgMed":{"fraOgMed":"2021-05-01","tilOgMed":null},"fradrag":[{"fraOgMed":"2021-05-01","tilOgMed":null,"vedtakstype":"Regulering","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000}],"fradragskategori":"Alderspensjon"}]},"eps":[{"fnr":"$epsFnr","perType":[{"vedtak":[{"type":"endring","måned":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"},"fradrag":[{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30","vedtakstype":"Endring","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000},{"type":"regulering","periodeOptionalTilOgMed":{"fraOgMed":"2021-05-01","tilOgMed":null},"fradrag":[{"fraOgMed":"2021-05-01","tilOgMed":null,"vedtakstype":"Regulering","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000}],"fradragskategori":"Alderspensjon"}]}]}""".trimIndent()

        deserEskternSupplementReguleringJson("""{"supplementId":"$ersId","bruker":null,"eps":[]}""") shouldBe ers
        deserEskternSupplementReguleringJson("""{"supplementId":null,"bruker":null,"eps":[]}""") shouldBe ersMedNull
        deserEskternSupplementReguleringJson(
            """{"supplementId":"$ersMedInnholdId","bruker":{"fnr":"$fnr","perType":[{"vedtak":[{"type":"endring","måned":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"},"fradrag":[{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30","vedtakstype":"Endring","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000},{"type":"regulering","periodeOptionalTilOgMed":{"fraOgMed":"2021-05-01","tilOgMed":null},"fradrag":[{"fraOgMed":"2021-05-01","tilOgMed":null,"vedtakstype":"Regulering","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000}],"fradragskategori":"Alderspensjon"}]},"eps":[{"fnr":"$epsFnr","perType":[{"vedtak":[{"type":"endring","måned":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"},"fradrag":[{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30","vedtakstype":"Endring","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000},{"type":"regulering","periodeOptionalTilOgMed":{"fraOgMed":"2021-05-01","tilOgMed":null},"fradrag":[{"fraOgMed":"2021-05-01","tilOgMed":null,"vedtakstype":"Regulering","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000}],"fradragskategori":"Alderspensjon"}]}]}""",
        ) shouldBe ersMedInnhold
    }
}
