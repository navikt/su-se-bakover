package no.nav.su.se.bakover.database.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.regulering.EksternSupplementReguleringJson.Companion.toDbJson
import no.nav.su.se.bakover.database.regulering.EksternVedtakJson.Companion.toDbJson
import no.nav.su.se.bakover.database.regulering.EksternVedtakstypeJson.Companion.toDbJson
import no.nav.su.se.bakover.database.regulering.FradragsperiodeJson.Companion.toDbJson
import no.nav.su.se.bakover.database.regulering.PerTypeJson.Companion.toDbJson
import no.nav.su.se.bakover.database.regulering.ReguleringssupplementForJson.Companion.toDbJson
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.nyEksternSupplementRegulering
import no.nav.su.se.bakover.test.nyEksternvedtakEndring
import no.nav.su.se.bakover.test.nyEksternvedtakRegulering
import no.nav.su.se.bakover.test.nyFradragperiodeEndring
import no.nav.su.se.bakover.test.nyFradragperiodeRegulering
import no.nav.su.se.bakover.test.nyReguleringssupplementFor
import no.nav.su.se.bakover.test.nyReguleringssupplementInnholdPerType
import org.junit.jupiter.api.Test
import java.util.UUID

class ReguleringssupplementJsonTest {
    private val fpe = nyFradragperiodeEndring()
    private val fpr = nyFradragperiodeRegulering()
    private val eve = nyEksternvedtakEndring()
    private val evr = nyEksternvedtakRegulering()
    private val pt = nyReguleringssupplementInnholdPerType()
    private val rf = nyReguleringssupplementFor(fnr = fnr)
    private val ersId = UUID.randomUUID()
    private val ers = nyEksternSupplementRegulering(id = ersId)

    @Test
    fun `serialiserer og deserialiserer`() {
        serialize(fpe.toDbJson()) shouldBe """{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30","vedtakstype":"Endring","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}"""
        serialize(fpr.toDbJson()) shouldBe """{"fraOgMed":"2021-05-01","tilOgMed":null,"vedtakstype":"Regulering","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}"""
        serialize(eve.toDbJson()) shouldBe """{"type":"endring","måned":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"},"fradrag":[{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30","vedtakstype":"Endring","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000}"""
        serialize(evr.toDbJson()) shouldBe """{"type":"regulering","periodeOptionalTilOgMed":{"fraOgMed":"2021-05-01","tilOgMed":null},"fradrag":[{"fraOgMed":"2021-05-01","tilOgMed":null,"vedtakstype":"Regulering","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000}"""
        serialize(pt.toDbJson()) shouldBe """{"vedtak":[{"type":"endring","måned":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"},"fradrag":[{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30","vedtakstype":"Endring","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000},{"type":"regulering","periodeOptionalTilOgMed":{"fraOgMed":"2021-05-01","tilOgMed":null},"fradrag":[{"fraOgMed":"2021-05-01","tilOgMed":null,"vedtakstype":"Regulering","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000}],"fradragskategori":"Alderspensjon"}"""
        serialize(rf.toDbJson()) shouldBe """{"fnr":"$fnr","perType":[{"vedtak":[{"type":"endring","måned":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"},"fradrag":[{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30","vedtakstype":"Endring","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000},{"type":"regulering","periodeOptionalTilOgMed":{"fraOgMed":"2021-05-01","tilOgMed":null},"fradrag":[{"fraOgMed":"2021-05-01","tilOgMed":null,"vedtakstype":"Regulering","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000}],"fradragskategori":"Alderspensjon"}]}"""
        serialize(ers.toDbJson()) shouldBe """{"supplementId":"$ersId","bruker":null,"eps":[]}"""

        deserialize<FradragsperiodeJson>("""{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30","vedtakstype":"Endring","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}""")
            .toDomain() shouldBe fpe
        deserialize<FradragsperiodeJson>("""{"fraOgMed":"2021-05-01","tilOgMed":null,"vedtakstype":"Regulering","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}""")
            .toDomain() shouldBe fpr
        deserialize<EksternVedtakJson>("""{"type":"endring","måned":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"},"fradrag":[{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30","vedtakstype":"Endring","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000}""")
            .toDomain() shouldBe eve
        deserialize<EksternVedtakJson>("""{"type":"regulering","periodeOptionalTilOgMed":{"fraOgMed":"2021-05-01","tilOgMed":null},"fradrag":[{"fraOgMed":"2021-05-01","tilOgMed":null,"vedtakstype":"Regulering","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000}""")
            .toDomain() shouldBe evr
        deserialize<PerTypeJson>("""{"vedtak":[{"type":"endring","måned":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"},"fradrag":[{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30","vedtakstype":"Endring","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000},{"type":"regulering","periodeOptionalTilOgMed":{"fraOgMed":"2021-05-01","tilOgMed":null},"fradrag":[{"fraOgMed":"2021-05-01","tilOgMed":null,"vedtakstype":"Regulering","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000}],"fradragskategori":"Alderspensjon"}""")
            .toDomain() shouldBe pt
        deserialize<ReguleringssupplementForJson>("""{"fnr":"$fnr","perType":[{"vedtak":[{"type":"endring","måned":{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30"},"fradrag":[{"fraOgMed":"2021-04-01","tilOgMed":"2021-04-30","vedtakstype":"Endring","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000},{"type":"regulering","periodeOptionalTilOgMed":{"fraOgMed":"2021-05-01","tilOgMed":null},"fradrag":[{"fraOgMed":"2021-05-01","tilOgMed":null,"vedtakstype":"Regulering","beløp":1000,"eksterndata":{"fnr":"11111111111","sakstype":"UFOREP","vedtakstype":"REGULERING","fraOgMed":"01.05.2021","tilOgMed":null,"bruttoYtelse":"10000","nettoYtelse":"11000","ytelseskomponenttype":"ST","bruttoYtelseskomponent":"10000","nettoYtelseskomponent":"11000"}}],"beløp":1000}],"fradragskategori":"Alderspensjon"}]}""")
            .toDomain() shouldBe rf
        EksternSupplementReguleringJson.deser("""{"supplementId":"$ersId","bruker":null,"eps":[]}""").toDomain() shouldBe ers
    }

    @Test
    fun `vedtakstype mapper riktig til og fra json`() {
        ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring.toDbJson() shouldBe EksternVedtakstypeJson.Endring
        ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering.toDbJson() shouldBe EksternVedtakstypeJson.Regulering

        EksternVedtakstypeJson.Endring.toDomain() shouldBe ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring
        EksternVedtakstypeJson.Regulering.toDomain() shouldBe ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering
    }
}
