package dokument.domain.pdf.bygger

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.brev.brevbaker.document.DocumentPDFRequest
import no.nav.brev.brevbaker.markup.dsl.ContentBuilder
import no.nav.brev.brevbaker.markup.dsl.OutlineBuilder
import no.nav.brev.brevbaker.pdfbygger.api.LetterPDFRequest
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.map

sealed interface PdfBestilling {
    data class Brev(val request: LetterPDFRequest) : PdfBestilling
    data class Dokument(val request: DocumentPDFRequest) : PdfBestilling
}

typealias Innhold = OutlineBuilder<ContentBuilder>

class PdfData private constructor(private val node: JsonNode?) {

    operator fun get(sti: String): PdfData =
        sti.split('.').fold(this) { verdi, nøkkel -> verdi.barn(nøkkel) }

    private fun barn(nøkkel: String): PdfData = when (node) {
        is ObjectNode -> of(node.get(nøkkel))
        is ArrayNode -> nøkkel.toIntOrNull()?.let { of(node.get(it)) } ?: TOM
        else -> TOM
    }

    val finnes: Boolean get() = node != null

    val sann: Boolean
        get() = when {
            node == null -> false
            node.isTextual -> node.textValue().isNotEmpty()
            node.isArray -> node.size() > 0
            node.isBoolean -> node.booleanValue()
            node.isNumber -> node.doubleValue() != 0.0
            else -> true
        }

    val tekst: String
        get() = tilStreng() ?: ""

    fun tilStreng(): String? = node?.let { tilStreng(it) }

    fun strengEllerNull(): String? {
        if (node == null) return null
        if (!node.isTextual) throw ClassCastException("Forventet tekst, men fikk ${node.nodeType}")
        return node.textValue()
    }

    val liste: List<PdfData>
        get() = (node as? ArrayNode)?.map { of(it) } ?: emptyList()

    val elementer: List<PdfData>
        get() = when (node) {
            is ArrayNode -> node.map { of(it) }
            is ObjectNode -> node.properties().map { of(it.value) }
            else -> emptyList()
        }

    fun er(verdi: String): Boolean = tilStreng() == verdi

    fun erIkke(verdi: String?): Boolean = tilStreng() != verdi

    fun størreEnn(tall: Int): Boolean {
        val n = node ?: throw IllegalArgumentException("Kan ikke sammenligne manglende verdi med $tall")
        require(n.isInt) { "Kan ikke sammenligne ${n.nodeType} med heltall" }
        return n.intValue() > tall
    }

    fun inneholderAlle(vararg verdier: String): Boolean {
        val liste = node as? ArrayNode ?: throw IllegalArgumentException("Forventet liste")
        return liste.map { it.textValue() }.containsAll(verdier.toList())
    }

    override fun toString(): String = "Verdi($node)"

    companion object {
        private val TOM = PdfData(null)
        private val objectMapper = ObjectMapper()

        fun of(node: JsonNode?): PdfData =
            if (node == null || node.isNull || node.isMissingNode) TOM else PdfData(node)

        fun parse(json: String): PdfData = of(objectMapper.readTree(json))

        private fun tilStreng(node: JsonNode): String? = when {
            node.isNull || node.isMissingNode -> null
            node.isTextual -> node.textValue()
            node.isBoolean -> node.booleanValue().toString()
            node.isInt -> node.intValue().toString()
            node.isLong -> node.longValue().toString()
            node.isBigInteger -> node.bigIntegerValue().toString()
            node.isBigDecimal -> node.decimalValue().toString()
            node.isDouble || node.isFloat -> node.doubleValue().toString()
            node.isObject -> node.properties().joinToString(", ", "{", "}") { (k, v) -> "$k=${tilStreng(v)}" }
            else -> node.toString()
        }
    }
}

class Kontekst private constructor(
    private val lag: (String) -> PdfData,
    private val forelder: Kontekst?,
) {
    operator fun get(sti: String): PdfData {
        val verdi = lag(sti)
        if (verdi.finnes) return verdi
        return forelder?.get(sti) ?: lag(sti)
    }

    fun med(verdi: PdfData): Kontekst = Kontekst({ verdi[it] }, this)

    fun medHash(vararg par: Pair<String, PdfData>): Kontekst {
        val hash = par.toMap()
        return Kontekst({ sti ->
            val første = sti.substringBefore('.')
            val rest = sti.substringAfter('.', "")
            val verdi = hash[første] ?: return@Kontekst PdfData.of(null)
            if (rest.isEmpty()) verdi else verdi[rest]
        }, this)
    }

    companion object {
        fun rot(verdi: PdfData): Kontekst = Kontekst({ verdi[it] }, null)
    }
}
