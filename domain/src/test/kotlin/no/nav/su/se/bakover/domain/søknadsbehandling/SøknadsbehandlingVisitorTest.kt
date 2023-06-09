package no.nav.su.se.bakover.domain.søknadsbehandling

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredFunctions

internal class SøknadsbehandlingVisitorTest {
    @Test
    fun `visitor interface burde implementere funksjon for alle sealed subclasses`() {
        withClue("Expected classes: \n${printSealedSubclasses(Søknadsbehandling::class)}") {
            SøknadsbehandlingVisitor::class.declaredFunctions.count() shouldBe countSealedSubclasses(Søknadsbehandling::class)
        }
    }

    private fun countSealedSubclasses(clazz: KClass<*>): Int {
        val subclasses = findDataClasses(clazz)
        return subclasses.size
    }

    private fun printSealedSubclasses(clazz: KClass<*>): String {
        val subclasses = findDataClasses(clazz)
        return subclasses.joinToString(separator = "\n") { it }
    }

    private fun findDataClasses(clazz: KClass<*>): Set<String> {
        tailrec fun helper(classesToCheck: List<KClass<*>>, accumulator: Set<String>): Set<String> {
            if (classesToCheck.isEmpty()) return accumulator

            val (head, tail) = classesToCheck.first() to classesToCheck.drop(1)
            val simpleName: String = head.toString()
            val newAccumulator = if (head.isData) accumulator + simpleName else accumulator
            return helper(tail + head.sealedSubclasses, newAccumulator)
        }
        return helper(listOf(clazz), emptySet())
    }
}
