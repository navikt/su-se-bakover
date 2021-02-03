package no.nav.su.se.bakover.domain.søknadsbehandling

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredFunctions

internal class SøknadsbehandlingVisitorTest {
    @Test
    fun `visitor interface burde implementere funksjon for alle sealed subclasses`() {
        SøknadsbehandlingVisitor::class.declaredFunctions.count() shouldBe findSealedSubclassCount(Søknadsbehandling::class)
    }

    fun findSealedSubclassCount(clazz: KClass<*>): Int {
        var sum = 0
        when (clazz.sealedSubclasses.count() == 0) {
            true -> sum += 1
            false -> clazz.sealedSubclasses.forEach {
                sum += findSealedSubclassCount(it)
            }
        }
        return sum
    }
}
