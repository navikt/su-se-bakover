package no.nav.su.se.bakover.service.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.behandling.Behandling
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class StatistikkSchemaValidatorTest {
    companion object {
        val gyldigBehandling: Statistikk.Behandling = Statistikk.Behandling(
            funksjonellTid = Tidspunkt.now(),
            tekniskTid = Tidspunkt.now(),
            mottattDato = LocalDate.now(),
            registrertDato = LocalDate.now(),
            behandlingId = UUID.randomUUID(),
            sakId = UUID.randomUUID(),
            saksnummer = 2021,
            behandlingStatus = Behandling.BehandlingsStatus.IVERKSATT_AVSLAG,
        )

        val gyldigSak = Statistikk.Sak(
            funksjonellTid = Tidspunkt.now(),
            tekniskTid = Tidspunkt.now(),
            opprettetDato = LocalDate.now(),
            sakId = UUID.randomUUID(),
            aktorId = 12345,
            saksnummer = 2021,
            aktorer = listOf(
                Statistikk.Aktør(
                    aktorId = 1235,
                    rolle = "rolle",
                    rolleBeskrivelse = "rollebeskrivelse"
                )
            ),
        )
    }

    @Test
    fun `alle felter utfylt med gyldige verdier er gyldig`() {
        StatistikkSchemaValidator.validerSak(
            objectMapper.writeValueAsString(gyldigSak)
        ) shouldBe true
    }

    @Test
    fun `valgfrie verdier trenger ikke å fylles ut`() {
        StatistikkSchemaValidator.validerSak(
            objectMapper.writeValueAsString(
                Statistikk.Sak(
                    funksjonellTid = Tidspunkt.now(),
                    tekniskTid = Tidspunkt.now(),
                    opprettetDato = LocalDate.now(),
                    sakId = UUID.randomUUID(),
                    aktorId = 2020,
                    saksnummer = 2025,
                )
            )
        ) shouldBe true
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
