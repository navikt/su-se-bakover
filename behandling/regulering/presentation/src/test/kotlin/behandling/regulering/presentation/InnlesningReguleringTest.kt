package behandling.regulering.presentation

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.ReguleringssupplementFor
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nyEksterndata
import no.nav.su.se.bakover.test.regulering.pesysFilCsvTestdata
import no.nav.su.se.bakover.web.routes.regulering.parseCSVFromString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import vilkår.inntekt.domain.grunnlag.Fradragstype

class InnlesningReguleringTest {

    @Test
    fun `leser og parser CSV`() {
        val data = pesysFilCsvTestdata
        val actual = parseCSVFromString(data)
        actual.getOrFail().let {
            it.first() shouldBe ReguleringssupplementFor(
                fnr = Fnr.tryCreate("11111111111") ?: fail("kunne ikke lage FNR"),
                perType = nonEmptyListOf(
                    ReguleringssupplementFor.PerType(
                        type = Fradragstype.Uføretrygd,
                        fradragsperioder = nonEmptyListOf(
                            ReguleringssupplementFor.PerType.Fradragsperiode(
                                fraOgMed = 1.april(2024),
                                tilOgMed = 30.april(2024),
                                beløp = 28261,
                                vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring,
                                eksterndata = nyEksterndata(
                                    fnr = "11111111111",
                                    sakstype = "UFOREP",
                                    vedtakstype = "ENDRING",
                                    fraOgMed = "01.04.2024",
                                    tilOgMed = "30.04.2024",
                                    bruttoYtelse = "28261",
                                    nettoYtelse = "28261",
                                    ytelseskomponenttype = "UT_ORDINER",
                                    bruttoYtelseskomponent = "26062",
                                    nettoYtelseskomponent = "26062",
                                ),
                            ),
                            ReguleringssupplementFor.PerType.Fradragsperiode(
                                fraOgMed = 1.april(2024),
                                tilOgMed = 30.april(2024),
                                beløp = 28261,
                                vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring,
                                eksterndata = nyEksterndata(
                                    fnr = "11111111111",
                                    sakstype = "UFOREP",
                                    vedtakstype = "ENDRING",
                                    fraOgMed = "01.04.2024",
                                    tilOgMed = "30.04.2024",
                                    bruttoYtelse = "28261",
                                    nettoYtelse = "28261",
                                    ytelseskomponenttype = "UT_GJT",
                                    bruttoYtelseskomponent = "2199",
                                    nettoYtelseskomponent = "2199",
                                ),
                            ),
                            ReguleringssupplementFor.PerType.Fradragsperiode(
                                fraOgMed = 1.april(2024),
                                tilOgMed = 30.april(2024),
                                beløp = 28261,
                                vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring,
                                eksterndata = nyEksterndata(
                                    fnr = "11111111111",
                                    sakstype = "UFOREP",
                                    vedtakstype = "ENDRING",
                                    fraOgMed = "01.04.2024",
                                    tilOgMed = "30.04.2024",
                                    bruttoYtelse = "28261",
                                    nettoYtelse = "28261",
                                    ytelseskomponenttype = "UT_TSB",
                                    bruttoYtelseskomponent = "0",
                                    nettoYtelseskomponent = "0",
                                ),
                            ),
                            ReguleringssupplementFor.PerType.Fradragsperiode(
                                fraOgMed = 1.mai(2024),
                                tilOgMed = null,
                                beløp = 29538,
                                vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering,
                                eksterndata = nyEksterndata(
                                    fnr = "11111111111",
                                    sakstype = "UFOREP",
                                    vedtakstype = "REGULERING",
                                    fraOgMed = "01.05.2024",
                                    tilOgMed = null,
                                    bruttoYtelse = "29538",
                                    nettoYtelse = "29538",
                                    ytelseskomponenttype = "UT_ORDINER",
                                    bruttoYtelseskomponent = "27339",
                                    nettoYtelseskomponent = "27339",
                                ),
                            ),
                            ReguleringssupplementFor.PerType.Fradragsperiode(
                                fraOgMed = 1.mai(2024),
                                tilOgMed = null,
                                beløp = 29538,
                                vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering,
                                eksterndata = nyEksterndata(
                                    fnr = "11111111111",
                                    sakstype = "UFOREP",
                                    vedtakstype = "REGULERING",
                                    fraOgMed = "01.05.2024",
                                    tilOgMed = null,
                                    bruttoYtelse = "29538",
                                    nettoYtelse = "29538",
                                    ytelseskomponenttype = "UT_GJT",
                                    bruttoYtelseskomponent = "2199",
                                    nettoYtelseskomponent = "2199",
                                ),
                            ),
                            ReguleringssupplementFor.PerType.Fradragsperiode(
                                fraOgMed = 1.mai(2024),
                                tilOgMed = null,
                                beløp = 29538,
                                vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering,
                                eksterndata = nyEksterndata(
                                    fnr = "11111111111",
                                    sakstype = "UFOREP",
                                    vedtakstype = "REGULERING",
                                    fraOgMed = "01.05.2024",
                                    tilOgMed = null,
                                    bruttoYtelse = "29538",
                                    nettoYtelse = "29538",
                                    ytelseskomponenttype = "UT_TSB",
                                    bruttoYtelseskomponent = "0",
                                    nettoYtelseskomponent = "0",
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
    }
}
