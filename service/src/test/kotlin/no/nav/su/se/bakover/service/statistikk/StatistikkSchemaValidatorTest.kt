package no.nav.su.se.bakover.service.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
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
            behandlingStatus = BehandlingsStatus.IVERKSATT_AVSLAG.toString(),
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            avsluttet = true,
        )

        val gyldigSak = Statistikk.Sak(
            funksjonellTid = Tidspunkt.now(),
            tekniskTid = Tidspunkt.now(),
            opprettetDato = LocalDate.now(),
            sakId = UUID.randomUUID(),
            aktorId = 1235,
            saksnummer = 2021,
        )
    }

    @Test
    fun `sak konstruert med påkrevde + defaultverdier er gyldig`() {
        StatistikkSchemaValidator.validerSak(objectMapper.writeValueAsString(gyldigSak)) shouldBe true
    }

    @Test
    fun `sak med valgfrie verdier satt til null er gyldig`() {
        StatistikkSchemaValidator.validerSak(
            objectMapper.writeValueAsString(
                Statistikk.Sak(
                    funksjonellTid = Tidspunkt.now(),
                    tekniskTid = Tidspunkt.now(),
                    opprettetDato = LocalDate.now(),
                    sakId = UUID.randomUUID(),
                    aktorId = 2020,
                    saksnummer = 2025,
                    ytelseTypeBeskrivelse = null,
                    sakStatusBeskrivelse = null,
                    aktorer = null,
                    underType = null,
                    underTypeBeskrivelse = null,
                )
            )
        ) shouldBe true
    }

    @Test
    fun `json struktur med ugyldig datoformat validerer ikke`() {
        //language=json
        StatistikkSchemaValidator.validerSak(
            """
            {
              "funksjonellTid": "dette datoformatet er ikke gyldig",
              "tekniskTid": "2020-12-18T07:00:28.673461Z",
              "opprettetDato": "2020-12-18",
              "sakId": "eaf4f024-1e5a-45b0-8d35-be0fa30299fa",
              "aktorId": 1235,
              "saksnummer": "2021",
              "ytelseType": "SU",
              "ytelseTypeBeskrivelse": "Supplerende stønad",
              "sakStatus": "OPPRETTET",
              "avsender": "su-se-bakover",
              "versjon": -1,
              "underType": "SUUFORE",
              "underTypeBeskrivelse": "Supplerende stønad for uføre flyktninger"
            }
            """.trimIndent()
        ) shouldBe false
    }

    @Test
    fun `json struktur med ugyldig type validerer ikke`() {
        //language=json
        StatistikkSchemaValidator.validerSak(
            """
            {
              "funksjonellTid": "2020-12-18T07:00:28.673461Z",
              "tekniskTid": "2020-12-18T07:00:28.673461Z",
              "opprettetDato": "2020-12-18",
              "sakId": "eaf4f024-1e5a-45b0-8d35-be0fa30299fa",
              "aktorId": 1235,
              "saksnummer": "2021",
              "ytelseType": "SU",
              "ytelseTypeBeskrivelse": "Supplerende stønad",
              "sakStatus": "OPPRETTET",
              "avsender": "su-se-bakover",
              "versjon": "denne skal da ikke være en string",
              "underType": "SUUFORE",
              "underTypeBeskrivelse": "Supplerende stønad for uføre flyktninger"
            }
            """.trimIndent()
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
        StatistikkSchemaValidator.validerBehandling(objectMapper.writeValueAsString(gyldigBehandling)) shouldBe true
    }
}
