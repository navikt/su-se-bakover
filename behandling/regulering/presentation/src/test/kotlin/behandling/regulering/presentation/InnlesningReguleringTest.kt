package behandling.regulering.presentation

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.supplement.Eksternvedtak
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nyEksterndata
import no.nav.su.se.bakover.test.nyFradragperiodeRegulering
import no.nav.su.se.bakover.test.regulering.pesysFilCsvUforepTestData
import no.nav.su.se.bakover.web.routes.regulering.uttrekk.pesys.parseCSVFromString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import vilkår.inntekt.domain.grunnlag.Fradragstype

class InnlesningReguleringTest {

    @Test
    fun `leser og parser CSV`() {
        val data = pesysFilCsvUforepTestData
        val actual = parseCSVFromString(data, fixedClock)
        actual.getOrFail().let {
            it.first() shouldBe ReguleringssupplementFor(
                fnr = Fnr.tryCreate("11111111111") ?: fail("kunne ikke lage FNR"),
                perType = nonEmptyListOf(
                    ReguleringssupplementFor.PerType(
                        kategori = Fradragstype.Uføretrygd.kategori,
                        vedtak = nonEmptyListOf(
                            Eksternvedtak.Endring(
                                periode = PeriodeMedOptionalTilOgMed(1.april(2024), 30.april(2024)),
                                beløp = 14241,
                                fradrag = nonEmptyListOf(
                                    ReguleringssupplementFor.PerType.Fradragsperiode(
                                        fraOgMed = 1.april(2024),
                                        tilOgMed = 30.april(2024),
                                        beløp = 14241,
                                        vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring,
                                        eksterndata = nyEksterndata(
                                            fnr = "11111111111",
                                            sakstype = "UFOREP",
                                            vedtakstype = "ENDRING",
                                            fraOgMed = "01.04.2024",
                                            tilOgMed = "30.04.2024",
                                            bruttoYtelse = "15529",
                                            nettoYtelse = "14241",
                                            ytelseskomponenttype = "UT_ORDINER",
                                            bruttoYtelseskomponent = "14811",
                                            nettoYtelseskomponent = "13583",
                                        ),
                                    ),
                                    ReguleringssupplementFor.PerType.Fradragsperiode(
                                        fraOgMed = 1.april(2024),
                                        tilOgMed = 30.april(2024),
                                        beløp = 14241,
                                        vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring,
                                        eksterndata = nyEksterndata(
                                            fnr = "11111111111",
                                            sakstype = "UFOREP",
                                            vedtakstype = "ENDRING",
                                            fraOgMed = "01.04.2024",
                                            tilOgMed = "30.04.2024",
                                            bruttoYtelse = "15529",
                                            nettoYtelse = "14241",
                                            ytelseskomponenttype = "UT_GJT",
                                            bruttoYtelseskomponent = "718",
                                            nettoYtelseskomponent = "658",
                                        ),
                                    ),
                                ),
                            ),
                            Eksternvedtak.Regulering(
                                periode = PeriodeMedOptionalTilOgMed(
                                    fraOgMed = 1.mai(2024),
                                    tilOgMed = null,
                                ),
                                beløp = 16255,
                                fradrag = nonEmptyListOf(
                                    nyFradragperiodeRegulering(
                                        fraOgMed = 1.mai(2024),
                                        tilOgMed = null,
                                        vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering,
                                        beløp = 16255,
                                        eksterndata = nyEksterndata(
                                            fnr = "11111111111",
                                            sakstype = "UFOREP",
                                            vedtakstype = "REGULERING",
                                            fraOgMed = "01.05.2024",
                                            tilOgMed = null,
                                            bruttoYtelse = "16255",
                                            nettoYtelse = "16255",
                                            ytelseskomponenttype = "UT_GJT",
                                            bruttoYtelseskomponent = "718",
                                            nettoYtelseskomponent = "718",
                                        ),
                                    ),
                                    nyFradragperiodeRegulering(
                                        fraOgMed = 1.mai(2024),
                                        tilOgMed = null,
                                        vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering,
                                        beløp = 16255,
                                        eksterndata = nyEksterndata(
                                            fnr = "11111111111",
                                            sakstype = "UFOREP",
                                            vedtakstype = "REGULERING",
                                            fraOgMed = "01.05.2024",
                                            tilOgMed = null,
                                            bruttoYtelse = "16255",
                                            nettoYtelse = "16255",
                                            ytelseskomponenttype = "UT_ORDINER",
                                            bruttoYtelseskomponent = "15537",
                                            nettoYtelseskomponent = "15537",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `fnr som er på 10 tegn får '0' prependet`() {
        val data = """
            FNR;K_SAK_T;K_VEDTAK_T;FOM_DATO;TOM_DATO;BRUTTO;NETTO;K_YTELSE_KOMP_T;BRUTTO_YK;NETTO_YK
            1111111111;UFOREP;ENDRING;01.04.2024;30.04.2024;15529;14241;UT_ORDINER;14811;13583
            1111111111;UFOREP;ENDRING;01.04.2024;30.04.2024;15529;14241;UT_GJT;718;658
            1111111111;UFOREP;REGULERING;01.05.2024;;16255;16255;UT_GJT;718;718
            1111111111;UFOREP;REGULERING;01.05.2024;;16255;16255;UT_ORDINER;15537;15537
        """.trimIndent()

        val actual = parseCSVFromString(data, fixedClock)

        actual.getOrFail().let {
            it.size shouldBe 1
            it.first().fnr shouldBe Fnr("01111111111")
        }
    }

    @Test
    fun `endringsvedtak sin tilOgMed kan være null`() {
        val data = """
            FNR;K_SAK_T;K_VEDTAK_T;FOM_DATO;TOM_DATO;BRUTTO;NETTO;K_YTELSE_KOMP_T;BRUTTO_YK;NETTO_YK
            1111111111;UFOREP;ENDRING;01.04.2024;;15529;14241;UT_ORDINER;14811;13583
            1111111111;UFOREP;ENDRING;01.04.2024;;15529;14241;UT_GJT;718;658
        """.trimIndent()

        val actual = parseCSVFromString(data, fixedClock)

        actual.getOrFail().let {
            it.size shouldBe 1
            it.first().fnr shouldBe Fnr("01111111111")
            it.first().perType.let {
                it.size shouldBe 1
                it.first().vedtak.let {
                    it.size shouldBe 1
                    it.forEach {
                        it.tilOgMed shouldBe null
                    }
                }
            }
        }
    }

    @Test
    fun `Håndterer newline`() {
        val data = """
            FNR;K_SAK_T;K_VEDTAK_T;FOM_DATO;TOM_DATO;BRUTTO;NETTO;K_YTELSE_KOMP_T;BRUTTO_YK;NETTO_YK
            1111111111;UFOREP;ENDRING;01.04.2024;;15529;14241;UT_ORDINER;14811;13583
            1111111111;UFOREP;ENDRING;01.04.2024;;15529;14241;UT_GJT;718;658
            
        """.trimIndent()

        val actual = parseCSVFromString(data, fixedClock)

        actual.getOrFail().let {
            it.size shouldBe 1
            it.first().fnr shouldBe Fnr("01111111111")
            it.first().perType.let {
                it.size shouldBe 1
                it.first().vedtak.let {
                    it.size shouldBe 1
                    it.forEach {
                        it.tilOgMed shouldBe null
                    }
                }
            }
        }
    }

    @Test
    fun `Hopper over tomme linjer`() {
        val data = """
            FNR;K_SAK_T;K_VEDTAK_T;FOM_DATO;TOM_DATO;BRUTTO;NETTO;K_YTELSE_KOMP_T;BRUTTO_YK;NETTO_YK
            
            1111111111;UFOREP;ENDRING;01.04.2024;;15529;14241;UT_ORDINER;14811;13583
            
            1111111111;UFOREP;ENDRING;01.04.2024;;15529;14241;UT_GJT;718;658
            
        """.trimIndent()

        val actual = parseCSVFromString(data, fixedClock)

        actual.getOrFail().let {
            it.size shouldBe 1
            it.first().fnr shouldBe Fnr("01111111111")
            it.first().perType.let {
                it.size shouldBe 1
                it.first().vedtak.let {
                    it.size shouldBe 1
                    it.forEach {
                        it.tilOgMed shouldBe null
                    }
                }
            }
        }
    }
}
