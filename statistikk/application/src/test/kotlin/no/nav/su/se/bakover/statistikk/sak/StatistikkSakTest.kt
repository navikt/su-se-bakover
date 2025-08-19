package no.nav.su.se.bakover.statistikk.sak

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.statistikk.KafkaStatistikkEventObserver
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.søknad.nySakMedNySøknad
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

/**
 * Tester behandlingsstatistikk hendelser i forbindelse med opprettelse av sak
 */
internal class StatistikkSakTest {

    @Test
    fun `publiserer opprettet sak`() {
        val kafkaPublisherMock: KafkaPublisher = mock()

        KafkaStatistikkEventObserver(
            publisher = kafkaPublisherMock,
            personService = mock {
                on { hentAktørIdMedSystembruker(any()) } doReturn AktørId("55").right()
            },
            clock = fixedClock,
            gitCommit = GitCommit("87a3a5155bf00b4d6854efcc24e8b929549c9302"),
            stønadStatistikkService = mock(),
        ).handle(
            StatistikkEvent.SakOpprettet(
                nySakMedNySøknad(sakId = UUID.fromString("5968ac62-12fb-481a-8c7f-508f129fd68b")).first,
            ),
        )
        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe "supstonad.aapen-su-sak-statistikk-v1" },
            // language=JSON
            argThat {
                // language=JSON
                JSONAssert.assertEquals(
                    """
                {
                  "funksjonellTid":"2021-01-01T01:02:03.456789Z",
                  "tekniskTid":"2021-01-01T01:02:03.456789Z",
                  "opprettetDato":"2021-01-01",
                  "sakId":"5968ac62-12fb-481a-8c7f-508f129fd68b",
                  "aktorId":55,
                  "saksnummer":"12345676",
                  "ytelseType":"SUUFORE",
                  "ytelseTypeBeskrivelse":"Supplerende stønad for uføre flyktninger",
                  "sakStatus":"OPPRETTET",
                  "sakStatusBeskrivelse":"Sak er opprettet men ingen vedtak er fattet.",
                  "avsender":"su-se-bakover",
                  "versjon":"87a3a5155bf00b4d6854efcc24e8b929549c9302"
                }
                    """.trimIndent(),
                    it,
                    true,
                )
            },
        )
    }
}
