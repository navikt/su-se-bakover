package no.nav.su.se.bakover.database.avkorting

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.simulering.simuleringFeilutbetaling
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class AvkortingVedRevurderingDbTest {

    private val ingen1 = AvkortingVedRevurdering.Uhåndtert.IngenUtestående
    private val ingen2 = ingen1.håndter()
    private val ingen3 = ingen2.håndter()
    private val ingen4 = ingen3.iverksett(UUID.randomUUID())

    private val kanIkke1 = ingen1.kanIkke()
    private val kanIkke2 = ingen2.kanIkke()
    private val kanIkke3 = ingen3.kanIkke()

    val revurderingId: UUID = UUID.randomUUID()
    private val varsel = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
        objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
            sakId = UUID.randomUUID(),
            revurderingId = revurderingId,
            simulering = simuleringFeilutbetaling(juni(2021)),
            opprettet = Tidspunkt.now(fixedClock),
        ),
    )
    private val utestående1 = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(avkortingsvarsel = varsel)
    private val utestående2 = utestående1.håndter()
    private val utestående3 = utestående2.håndter()
    private val utestående4 = AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
        avkortingsvarsel = varsel,
        annullerUtestående = utestående2.avkortingsvarsel,
    )
    private val utestående5 = AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel(
        avkortingsvarsel = varsel,
    )

    @Test
    fun `ingen utestående eller ny`() {
        val exp1 = """
            {
                "@type":"UHÅNDTERT_INGEN_UTESTÅENDE"
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp1, objectMapper.writeValueAsString(ingen1.toDb()), true)
        objectMapper.readValue<AvkortingVedRevurderingDb.Uhåndtert.IngenUtestående>(exp1)
            .toDomain() shouldBe beOfType<AvkortingVedRevurdering.Uhåndtert.IngenUtestående>()

        val exp2 = """
            {
                "@type":"DELVIS_HÅNDTERT_INGEN_UTESTÅENDE"
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp2, objectMapper.writeValueAsString(ingen2.toDb()), true)
        objectMapper.readValue<AvkortingVedRevurderingDb.DelvisHåndtert.IngenUtestående>(exp2)
            .toDomain() shouldBe beOfType<AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående>()

        val exp3 = """
            {
                "@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp3, objectMapper.writeValueAsString(ingen3.toDb()), true)
        objectMapper.readValue<AvkortingVedRevurderingDb.Håndtert.IngenNyEllerUtestående>(exp3)
            .toDomain() shouldBe beOfType<AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående>()

        val exp4 = """
            {
                "@type":"IVERKSATT_INGEN_NY_ELLER_UTESTÅENDE"
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp4, objectMapper.writeValueAsString(ingen4.toDb()), true)
        objectMapper.readValue<AvkortingVedRevurderingDb.Iverksatt.IngenNyEllerUtestående>(exp4)
            .toDomain() shouldBe beOfType<AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående>()
    }

    @Test
    fun `kan ikke`() {
        val exp4 = """
            {
                "@type":"UHÅNDTERT_KAN_IKKE",
                "uhåndtert": {"@type":"UHÅNDTERT_INGEN_UTESTÅENDE"}
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp4, objectMapper.writeValueAsString(kanIkke1.toDb()), true)
        objectMapper.readValue<AvkortingVedRevurderingDb.Uhåndtert.KanIkkeHåndteres>(exp4)
            .toDomain() shouldBe beOfType<AvkortingVedRevurdering.Uhåndtert.KanIkkeHåndtere>()

        val exp5 = """
            {
                "@type":"DELVIS_KAN_IKKE",
                "delvisHåndtert": {"@type":"DELVIS_HÅNDTERT_INGEN_UTESTÅENDE"}
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp5, objectMapper.writeValueAsString(kanIkke2.toDb()), true)
        objectMapper.readValue<AvkortingVedRevurderingDb.DelvisHåndtert.KanIkkeHåndteres>(exp5)
            .toDomain() shouldBe beOfType<AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere>()

        val exp6 = """
            {
                "@type":"HÅNDTERT_KAN_IKKE",
                "håndtert": {"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp6, objectMapper.writeValueAsString(kanIkke3.toDb()), true)
        objectMapper.readValue<AvkortingVedRevurderingDb.Håndtert.KanIkkeHåndteres>(exp6)
            .toDomain() shouldBe beOfType<AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres>()
    }

    @Test
    fun `utestående`() {
        val exp8 = """
            {
                "@type":"UHÅNDTERT_UTESTÅENDE",
                "avkortingsvarsel":${objectMapper.writeValueAsString(varsel.toDb())}
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp8, objectMapper.writeValueAsString(utestående1.toDb()), true)
        objectMapper.readValue<AvkortingVedRevurderingDb.Uhåndtert.UteståendeAvkorting>(exp8)
            .toDomain() shouldBe beOfType<AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting>()

        val exp9 = """
            {
                "@type":"DELVIS_HÅNDTERT_ANNULLERT_UTESTÅENDE",
                "avkortingsvarsel":${objectMapper.writeValueAsString(varsel.toDb())}
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp9, objectMapper.writeValueAsString(utestående2.toDb()), true)
        objectMapper.readValue<AvkortingVedRevurderingDb.DelvisHåndtert.AnnullerUtestående>(exp9)
            .toDomain() shouldBe beOfType<AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående>()

        val exp10 = """
            {
                "@type":"HÅNDTERT_ANNULLERT_UTESTÅENDE",
                "avkortingsvarsel":${objectMapper.writeValueAsString(varsel.toDb())}
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp10, objectMapper.writeValueAsString(utestående3.toDb()), true)
        objectMapper.readValue<AvkortingVedRevurderingDb.Håndtert.AnnullerUtestående>(exp10)
            .toDomain() shouldBe beOfType<AvkortingVedRevurdering.Håndtert.AnnullerUtestående>()

        val exp11 = """
            {
                "@type":"HÅNDTERT_NY_OG_ANNULLERT_UTESTÅENDE",
                "avkortingsvarsel":${objectMapper.writeValueAsString(varsel.toDb())},
                "uteståendeAvkortingsvarsel":${objectMapper.writeValueAsString(varsel.toDb())}
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp11, objectMapper.writeValueAsString(utestående4.toDb()), true)
        objectMapper.readValue<AvkortingVedRevurderingDb.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående>(exp11)
            .toDomain() shouldBe beOfType<AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående>()

        val exp12 = """
            {
                "@type":"IVERKSATT_NY_OG_ANNULLERT_UTESTÅENDE",
                "avkortingsvarsel":${objectMapper.writeValueAsString(varsel.toDb())},
                "uteståendeAvkortingsvarsel":${objectMapper.writeValueAsString(varsel.annuller(revurderingId).toDb())}
            }
        """.trimIndent()
        JSONAssert.assertEquals(
            exp12,
            objectMapper.writeValueAsString(utestående4.iverksett(revurderingId).toDb()),
            true,
        )
        objectMapper.readValue<AvkortingVedRevurderingDb.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående>(
            exp12,
        )
            .toDomain() shouldBe beOfType<AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående>()

        val exp13 = """
            {
                "@type":"HÅNDTERT_NY",
                "avkortingsvarsel":${objectMapper.writeValueAsString(varsel.toDb())}
            }
        """.trimIndent()
        JSONAssert.assertEquals(exp13, objectMapper.writeValueAsString(utestående5.toDb()), true)
        objectMapper.readValue<AvkortingVedRevurderingDb.Håndtert.OpprettNyttAvkortingsvarsel>(exp13)
            .toDomain() shouldBe beOfType<AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel>()

        val exp14 = """
            {
                "@type":"IVERKSATT_NY",
                "avkortingsvarsel":${objectMapper.writeValueAsString(varsel.toDb())}
            }
        """.trimIndent()
        JSONAssert.assertEquals(
            exp14,
            objectMapper.writeValueAsString(utestående5.iverksett(UUID.randomUUID()).toDb()),
            true,
        )
        objectMapper.readValue<AvkortingVedRevurderingDb.Iverksatt.OpprettNyttAvkortingsvarsel>(exp14)
            .toDomain() shouldBe beOfType<AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel>()
    }
}
