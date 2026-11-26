package no.nav.su.se.bakover.service.brev.maltest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.PdfBestilling
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import no.nav.brev.brevbaker.markup.outline.Block
import no.nav.su.se.bakover.common.jsonNode
import no.nav.su.se.bakover.common.serialize
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/** En egenskap som skal gjelde for alle maler, uansett hvilket innhold de får. */
internal class MalEgenskap(
    val navn: String,
    val sjekk: (PdfInnhold) -> Unit,
)

private val lekkasje = Regex("""\bnull\b|\w+\(\w+=""")
private val mellomrom = Regex("""\s+""")

internal val malEgenskaper: List<MalEgenskap> = listOf(
    MalEgenskap("E1 lager bestilling uten å feile") { innhold ->
        bestillingFor(innhold)
    },

    MalEgenskap("E2 samme innhold gir lik bestilling") { innhold ->
        utenIder(bestillingFor(innhold)) shouldBe utenIder(bestillingFor(innhold))
    },

    MalEgenskap("E3 ingen tomme blokker eller ugyldige linjeskift") { innhold ->
        bestillingFor(innhold).blokkproblemer().shouldBeEmpty()
    },

    MalEgenskap("E4 tabellradene har like mange celler som kolonner") { innhold ->
        bestillingFor(innhold).toppblokker().filterIsInstance<Block.Table>().forEach { tabell ->
            tabell.rows.map { it.cells.size }.filter { it != tabell.header.colSpec.size }.shouldBeEmpty()
        }
    },

    MalEgenskap("E5 ingen null eller toString() i teksten") { innhold ->
        bestillingFor(innhold).alleTekster().filter { lekkasje.containsMatchIn(it) }.shouldBeEmpty()
    },

    MalEgenskap("E6 saksinformasjonen samsvarer med personalia") sjekk@{ innhold ->
        val personalia = innhold.personalia() ?: return@sjekk
        val saksinfo = bestillingFor(innhold).saksinfo() ?: return@sjekk
        saksinfo.navn?.let { navn ->
            navn shouldContain personalia.fornavn
            navn shouldContain personalia.etternavn
        }
        saksinfo.fødselsnummer?.let { it shouldBe personalia.fødselsnummer }
        saksinfo.saksnummer?.let { it shouldBe personalia.saksnummer.toString() }
    },

    // Sporer data, ikke ordlyd: det saksbehandleren skrev, skal finnes i brevet.
    MalEgenskap("E7 saksbehandlers fritekst kommer fram") sjekk@{ innhold ->
        val fritekst = innhold.fritekst() ?: return@sjekk
        val tekst = normaliser(bestillingFor(innhold).alleTekster().joinToString(" "))
        fritekst.lines().map(::normaliser).filter { it.isNotEmpty() }.forEach { linje ->
            tekst shouldContain linje
        }
    },

    MalEgenskap("E8 bestillingen kan serialiseres slik klienten gjør") { innhold ->
        serialize(bestillingFor(innhold).forespørsel()).shouldNotBeBlank()
    },
)

private fun normaliser(tekst: String): String = tekst.replace(mellomrom, " ").trim()

/** Elementene i markup-treet har id-er. Sammenligningen skal ikke avhenge av dem. */
private fun utenIder(bestilling: PdfBestilling): JsonNode =
    jsonNode(serialize(bestilling.forespørsel())).also { fjernIder(it) }

private fun fjernIder(node: JsonNode) {
    if (node is ObjectNode) node.remove("id")
    node.forEach { fjernIder(it) }
}

private fun PdfInnhold.personalia(): PersonaliaPdfInnhold? =
    feltverdier().firstNotNullOfOrNull { it as? PersonaliaPdfInnhold }

private fun PdfInnhold.fritekst(): String? =
    this::class.memberProperties.firstOrNull { it.name == "fritekst" }?.let { egenskap ->
        egenskap.isAccessible = true
        egenskap.getter.call(this) as? String
    }

private fun PdfInnhold.feltverdier(): List<Any?> =
    this::class.memberProperties.map { egenskap ->
        egenskap.isAccessible = true
        egenskap.getter.call(this)
    }
