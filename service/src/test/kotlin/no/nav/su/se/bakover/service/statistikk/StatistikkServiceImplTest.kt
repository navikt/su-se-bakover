package no.nav.su.se.bakover.service.statistikk

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.doNothing
import org.junit.jupiter.api.Test

internal class StatistikkServiceImplTest {
    private val sakTopicName = "supstonad.aapen-su-sak-statistikk-v1"
    private val behandlingTopicName = "supstonad.aapen-su-behandling-statistikk-v1"

    private val sak = StatistikkSchemaValidatorTest.gyldigSak

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
