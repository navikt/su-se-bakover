package no.nav.su.se.bakover.database.søknadsbehandling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingHandlingDb.Companion.toDb
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingshistorikkJson.Companion.toDbJson
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.test.enUkeEtterFixedTidspunkt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nySøknadsbehandlingshendelse
import no.nav.su.se.bakover.test.nySøknadsbehandlingshistorikk
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class SøknadsbehandlingshistorikkJsonTest {

    @Test
    fun `mapper søknadsbehandlinghistorikk til Json`() {
        val expectedTilhørendeId = SøknadsbehandlingId.generer()
        //language=json
        val expected = """
            {
                "historikk": [
                    {
                        "tidspunkt": "2021-01-01T01:02:03.456789Z",
                        "navIdent": "saksbehandler",
                        "handlingJson": {
                            "handling": "StartetBehandling",
                            "tilhørendeSøknadsbehandlingId": null
                        }
                    },
                    {
                        "tidspunkt": "2021-01-08T01:02:03.456789Z",
                        "navIdent": "saksbehandler",
                        "handlingJson": {
                            "handling": "OppdatertUførhet",
                            "tilhørendeSøknadsbehandlingId": null
                        }
                    },
                    {
                        "tidspunkt": "2021-01-08T01:02:03.456789Z",
                        "navIdent": "saksbehandler",
                        "handlingJson": {
                            "handling": "StartetFraEtAvslag",
                            "tilhørendeSøknadsbehandlingId": $expectedTilhørendeId
                        }
                    }
                ]}
        """.trimIndent()

        val actual = nySøknadsbehandlingshistorikk(
            nonEmptyListOf(
                nySøknadsbehandlingshendelse(
                    tidspunkt = fixedTidspunkt,
                    handling = SøknadsbehandlingsHandling.StartetBehandling,
                ),
                nySøknadsbehandlingshendelse(
                    tidspunkt = enUkeEtterFixedTidspunkt,
                    handling = SøknadsbehandlingsHandling.OppdatertUførhet,
                ),
                nySøknadsbehandlingshendelse(
                    tidspunkt = enUkeEtterFixedTidspunkt,
                    handling = SøknadsbehandlingsHandling.StartetBehandlingFraEtAvslag(expectedTilhørendeId),
                ),
            ),
        ).toDbJson()
        JSONAssert.assertEquals(expected, actual, true)
    }

    @Test
    fun `mapper json til søknadsbehandlinghistorikk`() {
        val expectedTilhørendeId = SøknadsbehandlingId.generer()
        SøknadsbehandlingshistorikkJson.toSøknadsbehandlingsHistorikk(
            //language=json
            """
            {
                "historikk": [
                    {
                        "tidspunkt": "2021-01-01T01:02:03.456789Z",
                        "navIdent": "saksbehandler",
                        "handlingJson": {
                            "handling": "StartetBehandling",
                            "tilhørendeSøknadsbehandlingId": null
                        }
                    },
                    {
                        "tidspunkt": "2021-01-08T01:02:03.456789Z",
                        "navIdent": "saksbehandler",
                        "handlingJson": {
                            "handling": "OppdatertUførhet",
                            "tilhørendeSøknadsbehandlingId": null
                        }
                    },
                    {
                        "tidspunkt": "2021-01-08T01:02:03.456789Z",
                        "navIdent": "saksbehandler",
                        "handlingJson": {
                            "handling": "StartetFraEtAvslag",
                            "tilhørendeSøknadsbehandlingId": "$expectedTilhørendeId"
                        }
                    }
                ]}
            """.trimIndent(),
        ) shouldBe nySøknadsbehandlingshistorikk(
            nonEmptyListOf(
                nySøknadsbehandlingshendelse(
                    tidspunkt = fixedTidspunkt,
                    handling = SøknadsbehandlingsHandling.StartetBehandling,
                ),
                nySøknadsbehandlingshendelse(
                    tidspunkt = enUkeEtterFixedTidspunkt,
                    handling = SøknadsbehandlingsHandling.OppdatertUførhet,
                ),
                nySøknadsbehandlingshendelse(
                    tidspunkt = enUkeEtterFixedTidspunkt,
                    handling = SøknadsbehandlingsHandling.StartetBehandlingFraEtAvslag(expectedTilhørendeId),
                ),
            ),
        )
    }

    @Test
    fun `mapper SøknadsbehandlingsHandling til riktig DB-type`() {
        SøknadsbehandlingsHandling.StartetBehandling.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.StartetBehandling)
        SøknadsbehandlingsHandling.OppdatertStønadsperiode.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.OppdatertStønadsperiode)
        SøknadsbehandlingsHandling.OppdatertUførhet.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.OppdatertUførhet)
        SøknadsbehandlingsHandling.OppdatertFlyktning.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.OppdatertFlyktning)
        SøknadsbehandlingsHandling.OppdatertLovligOpphold.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.OppdatertLovligOpphold)
        SøknadsbehandlingsHandling.OppdatertFastOppholdINorge.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.OppdatertFastOppholdINorge)
        SøknadsbehandlingsHandling.OppdatertInstitusjonsopphold.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.OppdatertInstitusjonsopphold)
        SøknadsbehandlingsHandling.OppdatertUtenlandsopphold.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.OppdatertUtenlandsopphold)
        SøknadsbehandlingsHandling.TattStillingTilEPS.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.TattStillingTilEPS)
        SøknadsbehandlingsHandling.OppdatertFormue.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.OppdatertFormue)
        SøknadsbehandlingsHandling.OppdatertPersonligOppmøte.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.OppdatertPersonligOppmøte)
        SøknadsbehandlingsHandling.FullførtBosituasjon.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.FullførtBosituasjon)
        SøknadsbehandlingsHandling.OppdatertFradragsgrunnlag.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.OppdatertFradrag)
        SøknadsbehandlingsHandling.Beregnet.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.Beregnet)
        SøknadsbehandlingsHandling.Simulert.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.Simulert)
        SøknadsbehandlingsHandling.SendtTilAttestering.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.SendtTilAttestering)
        SøknadsbehandlingsHandling.OppdatertOpplysningsplikt.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.OppdatertOpplysningsplikt)
        SøknadsbehandlingsHandling.Lukket.toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.Lukket)
        val expectedTilhørendeId = SøknadsbehandlingId.generer()
        SøknadsbehandlingsHandling.StartetBehandlingFraEtAvslag(expectedTilhørendeId).toDb() shouldBe HandlingJson(SøknadsbehandlingHandlingDb.StartetFraEtAvslag, expectedTilhørendeId.toString())
    }

    @Test
    fun `mapper SøknadsbehandlingsHandlingDb til riktig domene-type`() {
        SøknadsbehandlingHandlingDb.StartetBehandling.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.StartetBehandling
        SøknadsbehandlingHandlingDb.OppdatertStønadsperiode.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.OppdatertStønadsperiode
        SøknadsbehandlingHandlingDb.OppdatertUførhet.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.OppdatertUførhet
        SøknadsbehandlingHandlingDb.OppdatertFlyktning.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.OppdatertFlyktning
        SøknadsbehandlingHandlingDb.OppdatertLovligOpphold.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.OppdatertLovligOpphold
        SøknadsbehandlingHandlingDb.OppdatertFastOppholdINorge.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.OppdatertFastOppholdINorge
        SøknadsbehandlingHandlingDb.OppdatertInstitusjonsopphold.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.OppdatertInstitusjonsopphold
        SøknadsbehandlingHandlingDb.OppdatertUtenlandsopphold.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.OppdatertUtenlandsopphold
        SøknadsbehandlingHandlingDb.TattStillingTilEPS.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.TattStillingTilEPS
        SøknadsbehandlingHandlingDb.OppdatertFormue.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.OppdatertFormue
        SøknadsbehandlingHandlingDb.OppdatertPersonligOppmøte.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.OppdatertPersonligOppmøte
        SøknadsbehandlingHandlingDb.FullførtBosituasjon.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.FullførtBosituasjon
        SøknadsbehandlingHandlingDb.OppdatertFradrag.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.OppdatertFradragsgrunnlag
        SøknadsbehandlingHandlingDb.Beregnet.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.Beregnet
        SøknadsbehandlingHandlingDb.Simulert.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.Simulert
        SøknadsbehandlingHandlingDb.SendtTilAttestering.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.SendtTilAttestering
        SøknadsbehandlingHandlingDb.OppdatertOpplysningsplikt.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.OppdatertOpplysningsplikt
        SøknadsbehandlingHandlingDb.Lukket.toSøknadsbehandlingsHandling() shouldBe SøknadsbehandlingsHandling.Lukket
        val expectedTilhørendeId = SøknadsbehandlingId.generer()
        SøknadsbehandlingHandlingDb.StartetFraEtAvslag.toSøknadsbehandlingsHandling(expectedTilhørendeId) shouldBe SøknadsbehandlingsHandling.StartetBehandlingFraEtAvslag(expectedTilhørendeId)
    }
}
