package no.nav.su.se.bakover.web.routes

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test

internal class NaisRoutesKtTest {

    @Test
    fun naisRoutes() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            client.get("/isalive").apply {
                status.value shouldBe 200
                this.bodyAsText() shouldBe "ALIVE"
            }
            client.get("/isready").apply {
                status.value shouldBe 200
                this.bodyAsText() shouldBe "READY"
            }
            client.get("/metrics").apply {
                status.value shouldBe 200
                this.bodyAsText().also {
                    it.shouldContain("process_cpu_usage")
                    it.shouldContain("system_cpu_count")
                    it.shouldContain("system_load_average_1m")
                    it.shouldContain("jvm_threads_states_threads")
                }
            }
        }
    }
}
