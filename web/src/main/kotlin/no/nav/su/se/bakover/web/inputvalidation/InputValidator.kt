package no.nav.su.se.bakover.web.inputvalidation

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import no.nav.su.se.bakover.common.SikkerLogg
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.serialize
import org.slf4j.Logger
import kotlin.collections.mutableListOf
import kotlin.reflect.KProperty1

data class Felt(
    val navn: String,
    val verdi: String?,
    val maksLengde: Int,
)

data class UgyldigInput(
    val felt: String,
    val begrunnelse: String,
    val tegn: String? = null,
)

internal fun UgyldigInput.tilUgyldigFeltMelding(): String =
    if (tegn == null) begrunnelse else "$begrunnelse: '$tegn'"

/**
 * Lager et 400 Bad Request-svar med [UgyldigInputValideringFeilResponse] (`code = "ugyldig_felt_input"`).
 * `errors` har én oppføring per [UgyldigInput], slik at frontend viser én linje per feil
 * på formen `felt: begrunnelse`.
 */
internal fun List<UgyldigInput>.tilUgyldigFeltSvar(): Resultat = Resultat.json(
    httpCode = BadRequest,
    json = serialize(
        UgyldigInputValideringFeilResponse(
            message = "Ugyldig input",
            code = "ugyldig_felt_input",
            errors = map {
                UgyldigInputValideringsfeil(
                    felt = it.felt,
                    begrunnelse = it.tilUgyldigFeltMelding(),
                )
            },
        ),
    ),
)

internal fun sti(vararg ledd: KProperty1<*, *>): String = ledd.joinToString(".") { it.name }

internal object InputValidator {
    private val tillatteSkilletegn = setOf(
        ' ', '.', ',', '?', '!', '(', ')',
        '%', '*', ':', ';',
        '-', '\u2011', '–', '—',
        '\'', '"',
        '\u2018', '\u2019', '\u201C', '\u201D', // ‘, ’, “, ”
        '/', '+', '&', '_',
        '§', '=', '»', '«', '•',
        '…',
        '@',
        '½', // U+00BD
    )

    private val tillatteValutaTegn = setOf(
        '$', // Dollar
        '€', // Euro
        '£', // GBP
    )

    // Aksenttegn
    private val tillatteSpesialTegn = setOf(
        'é',
        'É',
        'è',
    )

    private val mistenkeligeMønstre = listOf(
        Regex("<\\s*/?\\s*script", RegexOption.IGNORE_CASE),
        Regex("javascript\\s*:", RegexOption.IGNORE_CASE),
        Regex("<\\s*iframe", RegexOption.IGNORE_CASE),
        Regex("<\\s*object", RegexOption.IGNORE_CASE),
        Regex("<\\s*embed", RegexOption.IGNORE_CASE),
        Regex("onerror\\s*=", RegexOption.IGNORE_CASE),
        Regex("onload\\s*=", RegexOption.IGNORE_CASE),
    )

    fun validerFritekst(
        verdi: String?,
    ): UgyldigInput? {
        val fritekst = InputFelter.FRITEKST
        val feil = mutableListOf<UgyldigInput>()
        feil.validerTekst(fritekst.name.lowercase(), verdi, fritekst.maksLengde)
        return feil.firstOrNull()
    }

    fun validerTekst(
        felt: String,
        verdi: String?,
        maksLengde: Int,
    ): UgyldigInput? {
        val feil = mutableListOf<UgyldigInput>()
        feil.validerTekst(felt, verdi, maksLengde)
        return feil.firstOrNull()
    }

    fun MutableList<UgyldigInput>.validerTekst(
        felt: String,
        verdi: String?,
        maksLengde: Int,
    ) {
        if (verdi == null) return

        val ulovlige = verdi.ulovligeTegn()
        val begrunnelse = when {
            verdi.length > maksLengde -> "for lang verdi, antall tegn ${verdi.length}, maks $maksLengde"
            verdi.inneholderForbudteKontrolltegn() -> "inneholder kontrolltegn"
            ulovlige != null -> "inneholder tegn utenfor tillatt tegnsett"
            verdi.harMistenkeligInnhold() -> "inneholder mistenkelig innhold"
            else -> null
        }

        if (begrunnelse != null) {
            add(UgyldigInput(felt, begrunnelse, ulovlige))
        }
    }

    fun List<Felt>.harKunGyldigeTegn(): Either<List<UgyldigInput>, Unit> {
        val feil = this.mapNotNull { validerTekst(it.navn, it.verdi, it.maksLengde) }
        return if (feil.isEmpty()) {
            Unit.right()
        } else {
            feil.left()
        }
    }

    private fun String.harMistenkeligInnhold(): Boolean {
        return mistenkeligeMønstre.any { it.containsMatchIn(this) }
    }

    // ASCII kontrolltegn
    private fun String.inneholderForbudteKontrolltegn(): Boolean {
        return this.any {
            (it.code in 0..31 && it != '\n' && it != '\r' && it != '\t') || it.code == 127
        }
    }

    private fun String.ulovligeTegn(): String? =
        this.filterNot { it.erTillattTegn() }.toCharArray().distinct().joinToString("").takeIf { it.isNotEmpty() }

    private fun Char.erTillattTegn(): Boolean {
        if (this == '\n' || this == '\r' || this == '\t') return true
        if (this in '0'..'9') return true
        if (this in 'a'..'z' || this in 'A'..'Z') return true
        if (this in setOf('æ', 'ø', 'å', 'Æ', 'Ø', 'Å')) return true
        if (this in tillatteSkilletegn) return true
        if (this in tillatteValutaTegn) return true
        if (this in tillatteSpesialTegn) return true

        return false
    }
}

data class UgyldigInputValideringFeilResponse(
    val message: String,
    val code: String,
    val errors: List<UgyldigInputValideringsfeil>,
)

data class UgyldigInputValideringsfeil(
    val felt: String,
    val begrunnelse: String,
)

private fun kunTegnSomSkalhaWarn(tegn: String?): Boolean =
    tegn != null && tegn.all { it == '<' || it == '>' || it == '\\' }

fun loggInputValidering(
    feil: List<UgyldigInput>,
    route: String,
    log: Logger,
    sikkerLogg: SikkerLogg,
) {
    if (feil.isEmpty()) {
        return
    }
    val logstreng = "VALIDERING: Feil i input for route: $route. Begrunnelse: ${feil.map { it.begrunnelse }}"
    if (feil.all { kunTegnSomSkalhaWarn(it.tegn) }) {
        log.warn(logstreng)
    } else {
        log.error(logstreng)
        sikkerLogg.error("VALIDERING: Feil i input for route: $route. Feil: $feil")
    }
}

fun loggInputValidering(
    feil: UgyldigInput?,
    route: String,
    log: Logger,
    sikkerLogg: SikkerLogg,
) {
    if (feil == null) {
        return
    }
    val feilmelding = feil.tilUgyldigFeltMelding()
    val loggstreng = "VALIDERING: Feil i input for route: $route. feilmelding: $feilmelding"
    if (kunTegnSomSkalhaWarn(feil.tegn)) {
        log.warn(loggstreng)
    } else {
        log.error(loggstreng)
        sikkerLogg.error("VALIDERING: Feil i input for route: $route. Feil: $feil")
    }
}
