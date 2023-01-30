package no.nav.su.se.bakover.database.søknadsbehandling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingHandlingDb.Companion.toDb
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingshistorikkJson.Companion.toDbJson
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
        //language=json
        val expected = """
            {
                "historikk": [
                    {
                        "tidspunkt": "2021-01-01T01:02:03.456789Z",
                        "navIdent": "saksbehandler",
                        "handling": "StartetBehandling"
                    },
                    {
                        "tidspunkt": "2021-01-08T01:02:03.456789Z",
                        "navIdent": "saksbehandler",
                        "handling": "OppdatertUførhet"
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
            ),
        ).toDbJson()
        JSONAssert.assertEquals(expected, actual, true)
    }

    @Test
    fun `mapper json til søknadsbehandlinghistorikk`() {
        SøknadsbehandlingshistorikkJson.toSøknadsbehandlingsHistorikk(
            """
            {
                "historikk": [
                    {
                        "tidspunkt": "2021-01-01T01:02:03.456789Z",
                        "navIdent": "saksbehandler",
                        "handling": "StartetBehandling"
                    },
                    {
                        "tidspunkt": "2021-01-08T01:02:03.456789Z",
                        "navIdent": "saksbehandler",
                        "handling": "OppdatertUførhet"
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
            ),
        )
    }

    @Test
    fun `mapper SøknadsbehandlingsHandling til riktig DB-type`() {
        SøknadsbehandlingsHandling.StartetBehandling.toDb() shouldBe SøknadsbehandlingHandlingDb.StartetBehandling
        SøknadsbehandlingsHandling.OppdatertStønadsperiode.toDb() shouldBe SøknadsbehandlingHandlingDb.OppdatertStønadsperiode
        SøknadsbehandlingsHandling.OppdatertUførhet.toDb() shouldBe SøknadsbehandlingHandlingDb.OppdatertUførhet
        SøknadsbehandlingsHandling.OppdatertFlyktning.toDb() shouldBe SøknadsbehandlingHandlingDb.OppdatertFlyktning
        SøknadsbehandlingsHandling.OppdatertLovligOpphold.toDb() shouldBe SøknadsbehandlingHandlingDb.OppdatertLovligOpphold
        SøknadsbehandlingsHandling.OppdatertFastOppholdINorge.toDb() shouldBe SøknadsbehandlingHandlingDb.OppdatertFastOppholdINorge
        SøknadsbehandlingsHandling.OppdatertInstitusjonsopphold.toDb() shouldBe SøknadsbehandlingHandlingDb.OppdatertInstitusjonsopphold
        SøknadsbehandlingsHandling.OppdatertUtenlandsopphold.toDb() shouldBe SøknadsbehandlingHandlingDb.OppdatertUtenlandsopphold
        SøknadsbehandlingsHandling.TattStillingTilEPS.toDb() shouldBe SøknadsbehandlingHandlingDb.TattStillingTilEPS
        SøknadsbehandlingsHandling.OppdatertFormue.toDb() shouldBe SøknadsbehandlingHandlingDb.OppdatertFormue
        SøknadsbehandlingsHandling.OppdatertPersonligOppmøte.toDb() shouldBe SøknadsbehandlingHandlingDb.OppdatertPersonligOppmøte
        SøknadsbehandlingsHandling.FullførtBosituasjon.toDb() shouldBe SøknadsbehandlingHandlingDb.FullførtBosituasjon
        SøknadsbehandlingsHandling.OppdatertFradragsgrunnlag.toDb() shouldBe SøknadsbehandlingHandlingDb.OppdatertFradrag
        SøknadsbehandlingsHandling.Beregnet.toDb() shouldBe SøknadsbehandlingHandlingDb.Beregnet
        SøknadsbehandlingsHandling.Simulert.toDb() shouldBe SøknadsbehandlingHandlingDb.Simulert
        SøknadsbehandlingsHandling.SendtTilAttestering.toDb() shouldBe SøknadsbehandlingHandlingDb.SendtTilAttestering
        SøknadsbehandlingsHandling.OppdatertOpplysningsplikt.toDb() shouldBe SøknadsbehandlingHandlingDb.OppdatertOpplysningsplikt
        SøknadsbehandlingsHandling.Lukket.toDb() shouldBe SøknadsbehandlingHandlingDb.Lukket
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
    }
}
