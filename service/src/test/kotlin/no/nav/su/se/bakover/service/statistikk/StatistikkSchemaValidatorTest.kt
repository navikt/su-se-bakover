package no.nav.su.se.bakover.service.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.util.UUID

// TODO: Utvid med tester som validerer fullstendige objekter, og flere tester for negative cases
internal class StatistikkSchemaValidatorTest {
    companion object {
        val gyldigBehandling: Statistikk.Behandling = Statistikk.Behandling(
            funksjonellTid = fixedTidspunkt,
            tekniskTid = fixedTidspunkt,
            mottattDato = fixedLocalDate,
            registrertDato = fixedLocalDate,
            behandlingId = UUID.randomUUID(),
            sakId = UUID.randomUUID(),
            saksnummer = 2021,
            behandlingStatus = BehandlingsStatus.IVERKSATT_AVSLAG.toString(),
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            behandlingYtelseDetaljer = listOf(Statistikk.BehandlingYtelseDetaljer(Statistikk.Stønadsklassifisering.BOR_ALENE)),
            avsluttet = true,
        )

        val gyldigSak = Statistikk.Sak(
            funksjonellTid = fixedTidspunkt,
            tekniskTid = fixedTidspunkt,
            opprettetDato = fixedLocalDate,
            sakId = UUID.randomUUID(),
            aktorId = 1235,
            saksnummer = 2021,
        )

        val gyldigStønad = Statistikk.Stønad(
            funksjonellTid = fixedTidspunkt,
            tekniskTid = fixedTidspunkt,
            stonadstype = Statistikk.Stønad.Stønadstype.SU_UFØR,
            sakId = UUID.randomUUID(),
            aktorId = 1234567890,
            sakstype = Statistikk.Stønad.Vedtakstype.SØKNAD,
            vedtaksdato = fixedLocalDate,
            vedtakstype = Statistikk.Stønad.Vedtakstype.SØKNAD,
            vedtaksresultat = Statistikk.Stønad.Vedtaksresultat.INNVILGET,
            behandlendeEnhetKode = "4815",
            ytelseVirkningstidspunkt = fixedLocalDate,
            gjeldendeStonadVirkningstidspunkt = fixedLocalDate,
            gjeldendeStonadStopptidspunkt = fixedLocalDate.plusYears(1),
            gjeldendeStonadUtbetalingsstart = fixedLocalDate,
            gjeldendeStonadUtbetalingsstopp = fixedLocalDate.plusYears(1),
            månedsbeløp = listOf(
                Statistikk.Stønad.Månedsbeløp(
                    måned = "todo",
                    stonadsklassifisering = Statistikk.Stønadsklassifisering.BOR_ALENE,
                    bruttosats = 10000,
                    nettosats = 5000,
                    inntekter = listOf(Statistikk.Inntekt("Arbeidsinntekt", 5000, "BRUKER", false)),
                    fradragSum = 5000,
                ),
            ),
            versjon = fixedTidspunkt.toEpochMilli(),
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
                    funksjonellTid = fixedTidspunkt,
                    tekniskTid = fixedTidspunkt,
                    opprettetDato = fixedLocalDate,
                    sakId = UUID.randomUUID(),
                    aktorId = 2020,
                    saksnummer = 2025,
                    ytelseTypeBeskrivelse = null,
                    sakStatusBeskrivelse = null,
                    aktorer = null,
                    underType = null,
                    underTypeBeskrivelse = null,
                ),
            ),
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
            """.trimIndent(),
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
            """.trimIndent(),
        ) shouldBe false
    }

    @Test
    fun `feil json struktur på behandling burde gi valideringsfeil`() {
        StatistikkSchemaValidator.validerBehandling(
            """
               {
               "bogus": true
               }
            """.trimIndent(),
        ) shouldBe false
    }

    @Test
    fun `gyldig behandling blir OK under validering`() {
        StatistikkSchemaValidator.validerBehandling(objectMapper.writeValueAsString(gyldigBehandling)) shouldBe true
    }

    @Test
    fun `gyldig stønad validerer OK`() {
        StatistikkSchemaValidator.validerStønad(objectMapper.writeValueAsString(gyldigStønad)) shouldBe true
    }

    @Test
    fun `behandling med ugyldig verdier i behandlingYtelseDetaljer-listen feiler`() {
        StatistikkSchemaValidator.validerBehandling(
            """
            {
            "funksjonellTid" : "2020-12-18T07:00:28.673461Z",
            "tekniskTid" : "2020-12-18T07:00:28.673461Z",
            "mottattDato" : "2020-12-18",
            "registrertDato" : "2020-12-18",
            "sakId" : "${UUID.randomUUID()}",
            "saksnummer" : "2021",
            "behandlingStatus" : "IVERKSATT_AVSLAG",
            "behandlingType" : "SOKNAD",
            "behandlingTypeBeskrivelse" : "Beskrivelse av søknaden",
            "behandlingYtelseDetaljer" : [55, 37, 18, 46],
            "avsluttet" : true,
            "utenlandstilsnitt": "NASJONAL",
            "behandlendeEnhetKode": "4815",
            "behandlendeEnhetType": "NORG",
            "ansvarligEnhetKode": "4815",
            "ansvarligEnhetType": "NORG",
            "totrinnsbehandling":  true,
            "avsender": "su-se-bakover",
            "versjon": 12345678
            }
            """.trimIndent(),
        ) shouldBe false
    }
}
