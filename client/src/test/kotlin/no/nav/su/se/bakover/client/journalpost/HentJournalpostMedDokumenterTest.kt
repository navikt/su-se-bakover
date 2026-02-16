package no.nav.su.se.bakover.client.journalpost

import arrow.core.getOrElse
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal class HentJournalpostMedDokumenterTest {

    @Test
    fun `setter digitalpostSendt true ved non-null digitalpostSendt objekt og query bruker __typename`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                token("Bearer onBehalfOfToken")
                    .willReturn(
                        WireMock.ok(
                            serialize(
                                hentJournalpostMedDokumenterResponse(
                                    journalpostId = "453982044",
                                    utsendingsinfo = UtsendingsinfoResponse(
                                        fysiskpostSendt = null,
                                        digitalpostSendt = DigitalpostSendtResponse(typeName = "DigitalpostSendt"),
                                        varselSendt = emptyList(),
                                    ),
                                ),
                            ),
                        ),
                    ),
            )

            val journalpost = runBlocking {
                setupClient(baseUrl()).hentJournalpostMedDokumenter(JournalpostId("453982044"))
            }.getOrElse {
                fail("Forventet success, fikk feil: $it")
            }

            val utsendingsinfo = journalpost.utsendingsinfo ?: fail("Forventet utsendingsinfo")
            utsendingsinfo.digitalpostSendt shouldBe true

            String(serveEvents.requests.first().request.body) shouldContain "__typename"
        }
    }

    @Test
    fun `beregner passert40TimerSidenVarsling deterministisk med fast clock`() {
        startedWireMockServerWithCorrelationId {
            val fixedNow = OffsetDateTime.of(2026, 2, 16, 12, 0, 0, 0, ZoneOffset.UTC)
            val merEnn40TimerSiden = fixedNow.minusHours(41).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val mindreEnn40TimerSiden = fixedNow.minusHours(39).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            stubFor(
                token("Bearer onBehalfOfToken")
                    .willReturn(
                        WireMock.ok(
                            serialize(
                                hentJournalpostMedDokumenterResponse(
                                    journalpostId = "453996079",
                                    utsendingsinfo = UtsendingsinfoResponse(
                                        fysiskpostSendt = null,
                                        digitalpostSendt = null,
                                        varselSendt = listOf(
                                            VarselSendtResponse(
                                                type = "SMS",
                                                adresse = "+4791111111",
                                                varslingstidspunkt = merEnn40TimerSiden,
                                            ),
                                            VarselSendtResponse(
                                                type = "EPOST",
                                                adresse = "test@example.com",
                                                varslingstidspunkt = mindreEnn40TimerSiden,
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
            )

            val journalpost = runBlocking {
                setupClient(
                    baseUrl = baseUrl(),
                    clock = Clock.fixed(fixedNow.toInstant(), ZoneId.of("UTC")),
                ).hentJournalpostMedDokumenter(JournalpostId("453996079"))
            }.getOrElse {
                fail("Forventet success, fikk feil: $it")
            }

            val varsel = journalpost.utsendingsinfo?.varselSendt ?: fail("Forventet varsel")
            varsel[0].passert40TimerSidenVarsling shouldBe true
            varsel[1].passert40TimerSidenVarsling shouldBe false
        }
    }

    @Test
    fun `bruker fallback zone for local datetime og setter null ved ugyldig tidspunkt`() {
        startedWireMockServerWithCorrelationId {
            val fixedNow = OffsetDateTime.of(2026, 2, 16, 12, 0, 0, 0, ZoneOffset.UTC)
            val lokalTidMindreEnn40TimerSiden =
                fixedNow.minusHours(39).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

            stubFor(
                token("Bearer onBehalfOfToken")
                    .willReturn(
                        WireMock.ok(
                            serialize(
                                hentJournalpostMedDokumenterResponse(
                                    journalpostId = "454012640",
                                    utsendingsinfo = UtsendingsinfoResponse(
                                        fysiskpostSendt = null,
                                        digitalpostSendt = null,
                                        varselSendt = listOf(
                                            VarselSendtResponse(
                                                type = "SMS",
                                                adresse = "+4792222222",
                                                varslingstidspunkt = lokalTidMindreEnn40TimerSiden,
                                            ),
                                            VarselSendtResponse(
                                                type = "EPOST",
                                                adresse = "invalid@example.com",
                                                varslingstidspunkt = "ugyldig-dato",
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
            )

            val journalpost = runBlocking {
                setupClient(
                    baseUrl = baseUrl(),
                    clock = Clock.fixed(fixedNow.toInstant(), ZoneId.of("UTC")),
                    localDateTimeFallbackZoneId = ZoneId.of("UTC"),
                ).hentJournalpostMedDokumenter(JournalpostId("454012640"))
            }.getOrElse {
                fail("Forventet success, fikk feil: $it")
            }

            val varsel = journalpost.utsendingsinfo?.varselSendt ?: fail("Forventet varsel")
            varsel[0].passert40TimerSidenVarsling shouldBe false
            varsel[1].passert40TimerSidenVarsling shouldBe null
        }
    }

    @Test
    fun `setter null ved manglende varslingstidspunkt og false ved eksakt 40 timer`() {
        startedWireMockServerWithCorrelationId {
            val fixedNow = OffsetDateTime.of(2026, 2, 16, 12, 0, 0, 0, ZoneOffset.UTC)
            val eksakt40TimerSiden = fixedNow.minusHours(40).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            stubFor(
                token("Bearer onBehalfOfToken")
                    .willReturn(
                        WireMock.ok(
                            serialize(
                                hentJournalpostMedDokumenterResponse(
                                    journalpostId = "454037807",
                                    utsendingsinfo = UtsendingsinfoResponse(
                                        fysiskpostSendt = null,
                                        digitalpostSendt = null,
                                        varselSendt = listOf(
                                            VarselSendtResponse(
                                                type = "SMS",
                                                adresse = "+4793333333",
                                                varslingstidspunkt = null,
                                            ),
                                            VarselSendtResponse(
                                                type = "EPOST",
                                                adresse = "boundary@example.com",
                                                varslingstidspunkt = eksakt40TimerSiden,
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
            )

            val journalpost = runBlocking {
                setupClient(
                    baseUrl = baseUrl(),
                    clock = Clock.fixed(fixedNow.toInstant(), ZoneId.of("UTC")),
                ).hentJournalpostMedDokumenter(JournalpostId("454037807"))
            }.getOrElse {
                fail("Forventet success, fikk feil: $it")
            }

            val varsel = journalpost.utsendingsinfo?.varselSendt ?: fail("Forventet varsel")
            varsel[0].passert40TimerSidenVarsling shouldBe null
            varsel[1].passert40TimerSidenVarsling shouldBe false
        }
    }
}

private fun hentJournalpostMedDokumenterResponse(
    journalpostId: String,
    utsendingsinfo: UtsendingsinfoResponse?,
): HentJournalpostMedDokumenterHttpResponse {
    return HentJournalpostMedDokumenterHttpResponse(
        data = HentJournalpostMedDokumenterResponse(
            journalpost = JournalpostMedDokumenterResponse(
                journalpostId = journalpostId,
                tittel = "Tittel",
                datoOpprettet = LocalDate.of(2026, 2, 16),
                utsendingsinfo = utsendingsinfo,
                dokumenter = emptyList(),
            ),
        ),
        errors = null,
    )
}
