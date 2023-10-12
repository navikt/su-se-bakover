package no.nav.su.se.bakover.domain

import dokument.domain.brev.BrevbestillingId
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.serialize
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class BrevbestillingIdTest {

    @Test
    fun `brevbestillingId i seg selv blir serialisert som forventet`() {
        val brevbestillingId = BrevbestillingId("123")

        val forventet = "\"123\""
        val serialized = serialize(brevbestillingId)

        serialized shouldBe forventet
    }

    @Test
    fun `brevbestillingId blir serialisert som forventet når den er i et objekt`() {
        val objektMedBrevbestillingId = object {
            private val brevbestillingId = BrevbestillingId("123")

            fun getBrevbestillingId() = brevbestillingId
        }

        //language=Json
        val forventetJson = """
            {
              "brevbestillingId": "123"
            }
        """.trimIndent()

        JSONAssert.assertEquals(forventetJson, serialize(objektMedBrevbestillingId), true)
    }
}
