package no.nav.su.se.bakover.service.brev.maltest

import dokument.domain.generer.DokumentMal
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.reflect.KClass

/**
 * Egenskapstester for alle brevmaler. Testene sjekker ikke ordlyd, den er godkjent faglig.
 * De sjekker egenskaper som skal gjelde uansett innhold: ingen, få eller mange elementer.
 *
 * Feiler en egenskap, skriver kotest ut seed-verdien, slik at eksempelet kan gjenskapes.
 */
internal class MalEgenskapTest {

    @TestFactory
    fun `alle maler har de generelle egenskapene`(): List<DynamicTest> =
        malEksempler.flatMap { (mal, eksempel) ->
            malEgenskaper.map { egenskap ->
                DynamicTest.dynamicTest("$mal: ${egenskap.navn}") {
                    runBlocking {
                        checkAll(ANTALL_EKSEMPLER, variasjoner(eksempel)) { innhold ->
                            egenskap.sjekk(innhold)
                        }
                    }
                }
            }
        }

    @Test
    fun `alle dokumentmaler har et eksempel`() {
        val dekket = malEksempler.map { (_, eksempel) -> eksempel.dokumentMal::class }.toSet()
        (bladklasser(DokumentMal::class) - dekket).shouldBeEmpty()
    }

    private fun bladklasser(klasse: KClass<*>): Set<KClass<*>> =
        if (klasse.isSealed) klasse.sealedSubclasses.flatMap { bladklasser(it) }.toSet() else setOf(klasse)

    private companion object {
        const val ANTALL_EKSEMPLER = 200
    }
}
