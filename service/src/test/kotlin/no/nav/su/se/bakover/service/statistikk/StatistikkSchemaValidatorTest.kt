package no.nav.su.se.bakover.service.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class StatistikkSchemaValidatorTest {
    @Test
    fun `tomt statistikkobjekt for sak er ikke gyldig`() {
        StatistikkSchemaValidator.validerSak(
            objectMapper.writeValueAsString(
                Statistikk.Sak(
                    funksjonellTid = "",
                    tekniskTid = "",
                    opprettetDato = "",
                    sakId = "",
                    aktorId = 0,
                    saksnummer = "",
                    ytelseType = "",
                    sakStatus = "",
                    avsender = "",
                    versjon = 0,
                    aktorer = listOf(),
                    underType = null,
                    ytelseTypeBeskrivelse = null,
                    underTypeBeskrivelse = null,
                    sakStatusBeskrivelse = null
                )
            )
        ) shouldBe false
    }

    @Test
    fun `alle felter utfylt er med gyldige verdier er gyldig`() {
        StatistikkSchemaValidator.validerSak(
            objectMapper.writeValueAsString(
                Statistikk.Sak(
                    funksjonellTid = Tidspunkt.now().toString(),
                    tekniskTid = Tidspunkt.now().toString(),
                    opprettetDato = LocalDate.now().toString(),
                    sakId = "12345",
                    aktorId = 12345,
                    saksnummer = "1235",
                    ytelseType = "ytelsetype",
                    sakStatus = "status",
                    avsender = "sup",
                    versjon = 1,
                    aktorer = listOf(
                        Statistikk.Aktør(
                            aktorId = 1235,
                            rolle = "rolle",
                            rolleBeskrivelse = "rollebeskrivelse"
                        )
                    ),
                    underType = "undertype",
                    ytelseTypeBeskrivelse = "ytelsetypebeskrivelse",
                    underTypeBeskrivelse = "unertypebeskrivelse",
                    sakStatusBeskrivelse = "sakstatusbeskrivelse"
                )
            )
        ) shouldBe true
    }

    @Test
    fun `valgfrie verdier trenger ikke å fylles ut`() {
        StatistikkSchemaValidator.validerSak(
            objectMapper.writeValueAsString(
                Statistikk.Sak(
                    funksjonellTid = Tidspunkt.now().toString(),
                    tekniskTid = Tidspunkt.now().toString(),
                    opprettetDato = LocalDate.now().toString(),
                    sakId = "12345",
                    aktorId = 12345,
                    saksnummer = "1235",
                    ytelseType = "ytelsetype",
                    sakStatus = "status",
                    avsender = "sup",
                    versjon = 1
                )
            )
        ) shouldBe true
    }

    @Test
    fun `feil format på tidspunkt gjør at validering feiler`() {
        StatistikkSchemaValidator.validerSak(
            objectMapper.writeValueAsString(
                Statistikk.Sak(
                    funksjonellTid = LocalDate.now().toString(),
                    tekniskTid = Tidspunkt.now().toString(),
                    opprettetDato = LocalDate.now().toString(),
                    sakId = "12345",
                    aktorId = 12345,
                    saksnummer = "1235",
                    ytelseType = "ytelsetype",
                    sakStatus = "status",
                    avsender = "sup",
                    versjon = 1
                )
            )
        ) shouldBe false
    }
}
