package no.nav.su.se.bakover.web.routes.behandling.beregning

import arrow.core.right
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.NyBeregningForSøknadsbehandling
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class NyBeregningForSøknadsbehandlingJsonTest {

    private val fraOgMed = "2021-01-01"
    private val fraOgMedDato: LocalDate = LocalDate.of(2021, 1, 1)

    private val tilOgMed = "2021-12-31"
    private val tilOgMedDato: LocalDate = LocalDate.of(2021, 12, 31)

    private val nyBeregningForSøknadsbehandling = NyBeregningForSøknadsbehandling(
        behandlingId = UUID.randomUUID(),
        saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
        fradrag = listOf(
            FradragFactory.ny(
                periode = Periode.create(
                    fraOgMed = fraOgMedDato,
                    tilOgMed = tilOgMedDato,
                ),
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 200.0,
                utenlandskInntekt = UtenlandskInntekt.create(
                    beløpIUtenlandskValuta = 88,
                    valuta = "SEK",
                    kurs = 8.8,
                ),
                tilhører = FradragTilhører.BRUKER,
            ),
        ),
    )

    @Test
    fun `kan deserialisere ny beregning for søknadsbehandling json`() {
        val nyBeregningForSøknadsbehandlingJson = deserialize<NyBeregningForSøknadsbehandlingJson>(
            //language=JSON
            """
                {
                     "stønadsperiode": {
                         "fraOgMed":"$fraOgMed",
                         "tilOgMed":"$tilOgMed"
                     },
                     "fraOgMed":"$fraOgMed",
                     "tilOgMed":"$tilOgMed",
                     "fradrag":[{
                         "type":"Arbeidsinntekt",
                         "beløp":200,
                         "utenlandskInntekt":{
                              "beløpIUtenlandskValuta": 88,
                              "valuta": "SEK",
                              "kurs": 8.8
                         },
                         "periode" : {
                            "fraOgMed":"$fraOgMed",
                            "tilOgMed":"$tilOgMed"
                         },
                         "tilhører": "BRUKER"
                     }]
                }
            """.trimIndent(),
        )
        assertSoftly {
            nyBeregningForSøknadsbehandlingJson shouldBe NyBeregningForSøknadsbehandlingJson(
                fradrag = listOf(
                    FradragJson(
                        periode = PeriodeJson(
                            fraOgMed = fraOgMed,
                            tilOgMed = tilOgMed,
                        ),
                        type = "Arbeidsinntekt",
                        beløp = 200.0,
                        utenlandskInntekt = UtenlandskInntektJson(
                            beløpIUtenlandskValuta = 88,
                            valuta = "SEK",
                            kurs = 8.8,
                        ),
                        tilhører = "BRUKER",
                    ),
                ),
            )
            nyBeregningForSøknadsbehandlingJson.toDomain(
                nyBeregningForSøknadsbehandling.behandlingId,
                nyBeregningForSøknadsbehandling.saksbehandler,
            ) shouldBe nyBeregningForSøknadsbehandling.right()
        }
    }
}
