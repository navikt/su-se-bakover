package no.nav.su.se.bakover.database.avkorting

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class AvkortingVedSøknadsbehandlingDbKtTest {

    val ingen1 = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående
    val ingen2 = ingen1.håndter()
    val ingen3 = ingen2.iverksett(UUID.randomUUID())

    val kanIkke1 = ingen1.kanIkke()
    val kanIkke2 = ingen2.kanIkke()

    val varsel = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
        objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
            sakId = UUID.randomUUID(),
            revurderingId = UUID.randomUUID(),
            simulering = simuleringFeilutbetaling(juni(2021)),
        ),
    )
    val utestående1 = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(avkortingsvarsel = varsel)
    val utestående2 = utestående1.håndter()
    val utestående3 = utestående2.iverksett(UUID.randomUUID())

    @Test
    fun `ingen utestående`() {
        val exp1 = """
            {
                "@type":"UHÅNDTERT_INGEN_UTESTÅENDE"
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp1, objectMapper.writeValueAsString(ingen1.toDb()), true)
        objectMapper.readValue<AvkortingVedSøknadsbehandlingDb.Uhåndtert.IngenUtestående>(exp1).toDomain(
            mock(),
            mock(),
        ) shouldBe beOfType<AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående>()

        val exp2 = """
            {
                "@type":"HÅNDTERT_INGEN_UTESTÅENDE"
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp2, objectMapper.writeValueAsString(ingen2.toDb()), true)
        objectMapper.readValue<AvkortingVedSøknadsbehandlingDb.Håndtert.IngenUtestående>(exp2).toDomain(
            mock(),
            mock(),
        ) shouldBe beOfType<AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående>()

        val exp3 = """
            {
                "@type":"IVERKSATT_INGEN_UTESTÅENDE"
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp3, objectMapper.writeValueAsString(ingen3.toDb()), true)
        objectMapper.readValue<AvkortingVedSøknadsbehandlingDb.Iverksatt.IngenUtestående>(exp3).toDomain(
            mock(),
            mock(),
        ) shouldBe beOfType<AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående>()
    }

    @Test
    fun `kan ikke`() {
        val exp4 = """
            {
                "@type":"UHÅNDTERT_KAN_IKKE"
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp4, objectMapper.writeValueAsString(kanIkke1.toDb()), true)
        objectMapper.readValue<AvkortingVedSøknadsbehandlingDb.Uhåndtert.KanIkkeHåndtere>(exp4).toDomain(
            mock(),
            mock(),
        ) shouldBe beOfType<AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere>()

        val exp5 = """
            {
                "@type":"HÅNDTERT_KAN_IKKE"
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp5, objectMapper.writeValueAsString(kanIkke2.toDb()), true)
        objectMapper.readValue<AvkortingVedSøknadsbehandlingDb.Håndtert.KanIkkeHåndtere>(exp5).toDomain(
            mock(),
            mock(),
        ) shouldBe beOfType<AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere>()
    }

    @Test
    fun `utestående`() {
        val exp6 = """
            {
                "@type":"UHÅNDTERT_UTESTÅENDE",
                "avkortingsvarselId":"${varsel.id}"
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp6, objectMapper.writeValueAsString(utestående1.toDb()), true)
        objectMapper.readValue<AvkortingVedSøknadsbehandlingDb.Uhåndtert.UteståendeAvkorting>(exp6).toDomain(
            mock { on { hent(any(), any()) } doReturn varsel },
            mock(),
        ) shouldBe beOfType<AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting>()

        val exp7 = """
            {
                "@type":"HÅNDTERT_AVKORTET_UTESTÅENDE",
                "avkortingsvarselId":"${varsel.id}"
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp7, objectMapper.writeValueAsString(utestående2.toDb()), true)
        objectMapper.readValue<AvkortingVedSøknadsbehandlingDb.Håndtert.AvkortUtestående>(exp7).toDomain(
            mock { on { hent(any(), any()) } doReturn varsel },
            mock(),
        ) shouldBe beOfType<AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående>()

        val exp8 = """
            {
                "@type":"IVERKSATT_AVKORTET_UTESTÅENDE",
                "avkortingsvarselId":"${varsel.id}"
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp8, objectMapper.writeValueAsString(utestående3.toDb()), true)
        objectMapper.readValue<AvkortingVedSøknadsbehandlingDb.Iverksatt.AvkortUtestående>(exp8).toDomain(
            mock { on { hent(any(), any()) } doReturn varsel.avkortet(UUID.randomUUID()) },
            mock(),
        ) shouldBe beOfType<AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående>()
    }
}
