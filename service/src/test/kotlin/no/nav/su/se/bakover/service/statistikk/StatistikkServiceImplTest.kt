package no.nav.su.se.bakover.service.statistikk

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.doNothing
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class StatistikkServiceImplTest {
    val sakTopicName = "supstonad.aapen-su-sak-statistikk-v1"
    val behandlingTopicName = "supstonad.aapen-su-behandling-statistikk-v1"

    val sak = Statistikk.Sak(
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

    @Test
    fun `Gyldig sak publiserer till kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }

        StatistikkServiceImpl(kafkaPublisherMock).publiser(sak)
        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe sakTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(sak) }
        )
    }

    @Test
    fun `Ugyldig sak publiserer ikke`() {
        val ugyldigSak = sak.copy(funksjonellTid = "")
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }

        StatistikkServiceImpl(kafkaPublisherMock).publiser(ugyldigSak)
        verifyZeroInteractions(kafkaPublisherMock)
    }

    @Test
    fun `Gyldig behandling publiserer till kafka`() {
        val kafkaPublisherMock: KafkaPublisher = mock {
            on { publiser(any(), any()) }.doNothing()
        }

        StatistikkServiceImpl(kafkaPublisherMock).publiser(StatistikkSchemaValidatorTest.gyldigBehandling)
        verify(kafkaPublisherMock).publiser(
            argThat { it shouldBe behandlingTopicName },
            argThat { it shouldBe objectMapper.writeValueAsString(StatistikkSchemaValidatorTest.gyldigBehandling) }
        )
    }
}
