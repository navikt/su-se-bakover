package no.nav.su.se.bakover.database.avkorting

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.test.avkorting.avkortingVedSøknadsbehandlingAvkortet
import no.nav.su.se.bakover.test.avkorting.avkortingVedSøknadsbehandlingSkalAvkortes
import no.nav.su.se.bakover.test.avkortingsvarselAvkortet
import no.nav.su.se.bakover.test.avkortingsvarselSkalAvkortes
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

internal class AvkortingVedSøknadsbehandlingDbKtTest {

    @Test
    fun `ikke vurdert`() {
        AvkortingVedSøknadsbehandling.IkkeVurdert.toDbJson() shouldBe null
        // Denne deserialiseres ikke, men styres av domenetypen.
    }

    @Test
    fun `ingen avkorting`() {
        AvkortingVedSøknadsbehandling.IngenAvkorting.toDbJson() shouldBe null
        // Denne deserialiseres ikke, men styres av domenetypen.
    }

    @Test
    fun `skal avkortes`() {
        val avkortingVedSøknadsbehandlingSkalAvkortes = avkortingVedSøknadsbehandlingSkalAvkortes()
        avkortingVedSøknadsbehandlingSkalAvkortes.toDbJson()!!.let {
            JSONAssert.assertEquals(
                """{"@type": "SKAL_AVKORTES","avkortingsvarsel":"ignored"}""",
                it,
                CustomComparator(
                    JSONCompareMode.STRICT,
                    Customization(
                        "avkortingsvarsel",
                    ) { _, _ -> true },
                ),
            )
            fromAvkortingDbJson(it) shouldBe avkortingVedSøknadsbehandlingSkalAvkortes
        }
    }

    @Test
    fun avkortet() {
        val avkortingVedSøknadsbehandlingAvkortet = avkortingVedSøknadsbehandlingAvkortet()
        avkortingVedSøknadsbehandlingAvkortet.toDbJson()!!.let {
            JSONAssert.assertEquals(
                """{"@type": "AVKORTET","avkortingsvarsel":"ignored"}""",
                it,
                CustomComparator(
                    JSONCompareMode.STRICT,
                    Customization(
                        "avkortingsvarsel",
                    ) { _, _ -> true },
                ),
            )
            fromAvkortingDbJson(it) shouldBe avkortingVedSøknadsbehandlingAvkortet
        }
    }

    @Nested
    inner class LegacyDeserialisering {

        @Nested
        inner class IngenUtestående {
            @Test
            fun UHÅNDTERT_INGEN_UTESTÅENDE() {
                fromAvkortingDbJson(
                    """{"@type":"UHÅNDTERT_INGEN_UTESTÅENDE"}""",
                ) shouldBe AvkortingVedSøknadsbehandling.IkkeVurdert
            }

            @Test
            fun HÅNDTERT_INGEN_UTESTÅENDE() {
                fromAvkortingDbJson(
                    """{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}""",
                ) shouldBe AvkortingVedSøknadsbehandling.IngenAvkorting
            }

            @Test
            fun IVERKSATT_INGEN_UTESTÅENDE() {
                fromAvkortingDbJson(
                    """{"@type":"IVERKSATT_INGEN_UTESTÅENDE"}""",
                ) shouldBe AvkortingVedSøknadsbehandling.IngenAvkorting
            }
        }

        @Nested
        inner class KanIkke {
            @Test
            fun UHÅNDTERT_KAN_IKKE() {
                fromAvkortingDbJson(
                    """
                    {
                        "@type":"UHÅNDTERT_KAN_IKKE",
                        "uhåndtert": {"@type":"UHÅNDTERT_INGEN_UTESTÅENDE"}
                    }
                    """.trimIndent(),
                ) shouldBe AvkortingVedSøknadsbehandling.IkkeVurdert
            }

            @Test
            fun HÅNDTERT_KAN_IKKE() {
                deserialize<AvkortingVedSøknadsbehandlingDb.Håndtert.KanIkkeHåndtere>(
                    """
                        {
                            "@type":"HÅNDTERT_KAN_IKKE",
                            "håndtert": {"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}
                        }
                    """.trimIndent(),
                ).toDomain() shouldBe AvkortingVedSøknadsbehandling.IngenAvkorting
            }

            @Test
            fun IVERKSATT_KAN_IKKE() {
                shouldThrowWithMessage<IllegalStateException>("Avventer migrering av AvkortingVedSøknadsbehandlingDb.Iverksatt.KanIkkeHåndtere - skal ikke være i bruk.") {
                    deserialize<AvkortingVedSøknadsbehandlingDb.Iverksatt.KanIkkeHåndtere>(
                        """
                        {
                            "@type":"IVERKSATT_KAN_IKKE",
                            "håndtert": {"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}
                        }
                        """.trimIndent(),
                    ).toDomain()
                }
            }

            @Nested
            inner class Utestående {
                @Test
                fun UHÅNDTERT_UTESTÅENDE() {
                    fromAvkortingDbJson(
                        """
                        {
                            "@type":"UHÅNDTERT_UTESTÅENDE",
                            "avkortingsvarsel":${serialize(avkortingsvarselSkalAvkortes().toDb())}
                        }
                        """.trimIndent(),
                    ) shouldBe AvkortingVedSøknadsbehandling.IkkeVurdert
                }

                @Test
                fun HÅNDTERT_AVKORTET_UTESTÅENDE() {
                    val avkortingsvarselSkalAvkortes = avkortingsvarselSkalAvkortes()
                    fromAvkortingDbJson(
                        """
                        {
                            "@type":"HÅNDTERT_AVKORTET_UTESTÅENDE",
                            "avkortingsvarsel":${serialize(avkortingsvarselSkalAvkortes.toDb())}
                        }
                        """.trimIndent(),
                    ) shouldBe AvkortingVedSøknadsbehandling.SkalAvkortes(
                        avkortingsvarselSkalAvkortes,
                    )
                }

                @Test
                fun IVERKSATT_AVKORTET_UTESTÅENDE() {
                    val avkortingsvarselAvkortet = avkortingsvarselAvkortet()
                    fromAvkortingDbJson(
                        """
                        {
                            "@type":"IVERKSATT_AVKORTET_UTESTÅENDE",
                            "avkortingsvarsel":${serialize(avkortingsvarselAvkortet.toDb())}
                        }
                        """.trimIndent(),
                    ) shouldBe AvkortingVedSøknadsbehandling.Avkortet(avkortingsvarselAvkortet)
                }
            }
        }
    }
}
