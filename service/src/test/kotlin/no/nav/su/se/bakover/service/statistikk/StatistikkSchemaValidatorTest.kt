package no.nav.su.se.bakover.service.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class StatistikkSchemaValidatorTest {
    companion object {
        val gyldigBehandling: Statistikk.Behandling = Statistikk.Behandling(
            funksjonellTid = Tidspunkt.now().toString(),
            tekniskTid = Tidspunkt.now().toString(),
            mottattDato = LocalDate.now().toString(),
            registrertDato = LocalDate.now().toString(),
            behandlingId = "55",
            relatertBehandlingId = null,
            sakId = UUID.randomUUID().toString(),
            saksnummer = "2022",
            behandlingType = "førstegangsbehandling",
            behandlingTypeBeskrivelse = null,
            behandlingStatus = "Avslått",
            utenlandstilsnitt = "nei",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "1",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = false,
            avsender = "su-se-bakover",
            versjon = 1,
        )

        val gyldigSak = Statistikk.Sak(
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
    }

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
            objectMapper.writeValueAsString(gyldigSak)
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
                gyldigSak.copy(funksjonellTid = "05 Jan 2020")
            )
        ) shouldBe false
    }

    @Test
    fun `feil json struktur på behandling burde gi valideringsfeil`() {
        StatistikkSchemaValidator.validerBehandling(
            """
               {
               "bogus": true
               }
            """.trimIndent()
        ) shouldBe false
    }

    @Test
    fun `gyldig behandling blir OK under validering`() {
        StatistikkSchemaValidator.validerBehandling(
            objectMapper.writeValueAsString(gyldigBehandling)
        ) shouldBe true
    }
}
