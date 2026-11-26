package no.nav.su.se.bakover.service.brev.maltest

import dokument.domain.generer.PdfInnhold
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/**
 * Lager variasjoner av et realistisk eksempel på brevinnhold: ingen, få eller mange elementer.
 *
 * Bare data-klassene i brevmodellen (`dokument.domain`) varieres. Domenetyper som `SøknadInnhold`, `Fnr`
 * og `Saksnummer` beholdes fra eksempelet, fordi de har egne regler som ikke hører hjemme i en maltest.
 *
 * - Valgfrie felt blir `null`.
 * - Lister får 0, 1, like mange som i eksempelet eller [MANGE] elementer (gjentatte og varierte kopier).
 * - Boolske felt og heltall slår av og på innhold (`false`/`true`, `0`/eksempelverdien).
 * - Enum-felt får tilfeldige verdier.
 * - Felt som heter noe med «fritekst», får tekst med avsnitt, linjeskift og spesialtegn.
 * - Andre tekstfelt beholdes, fordi noen av dem har et format malen leser (for eksempel datoer).
 */
internal fun variasjoner(eksempel: PdfInnhold): Arb<PdfInnhold> = Arb.choice(
    Arb.constant(eksempel),
    Arb.constant(varier(eksempel, Profil.TOM, Random(0)) as PdfInnhold),
    Arb.constant(varier(eksempel, Profil.FULL, Random(0)) as PdfInnhold),
    arbitrary { kilde -> varier(eksempel, Profil.TILFELDIG, kilde.random) as PdfInnhold },
)

internal enum class Profil {
    /** Alt valgfritt er borte: `null`, tomme lister, `false`, `0` og tom fritekst. */
    TOM,

    /** Alt er med: lange lister, `true` og lang fritekst med spesialtegn. */
    FULL,

    /** Hvert felt varieres for seg. */
    TILFELDIG,
}

private const val MANGE = 6

private const val VANSKELIGE_TEGN = "æøå ÆØÅ & % $ # _ { } ~ ^ \\ < > « » – \" '"

private val fritekster = listOf(
    "",
    "   ",
    "\n\n",
    "Én linje.",
    "Første linje\nAndre linje",
    "Første avsnitt\n\nAndre avsnitt",
    "Linje med Windows-linjeskift\r\nNeste linje",
    "Slutter med linjeskift\n",
    "Tegn: $VANSKELIGE_TEGN",
    "Lang tekst " + "ord ".repeat(400),
)

private fun varier(verdi: Any, profil: Profil, random: Random): Any {
    val klasse = verdi::class
    if (!klasse.isData || klasse.qualifiedName?.startsWith("dokument.domain.") != true) return verdi
    val konstruktør = klasse.primaryConstructor ?: return verdi
    val egenskaper = klasse.memberProperties.associateBy { it.name }
    val argumenter = konstruktør.parameters.associateWith { parameter ->
        val egenskap = egenskaper[parameter.name] ?: return verdi
        egenskap.isAccessible = true
        varierFelt(parameter, egenskap.getter.call(verdi), profil, random)
    }
    // Faller tilbake til eksempelet hvis en variasjon bryter en regel i konstruktøren.
    return runCatching {
        konstruktør.isAccessible = true
        konstruktør.callBy(argumenter)
    }.getOrElse { verdi }
}

private fun varierFelt(parameter: KParameter, verdi: Any?, profil: Profil, random: Random): Any? {
    if (verdi == null) return null
    if (parameter.type.isMarkedNullable && skalFjernes(profil, random)) return null
    return when {
        verdi is String && parameter.name.orEmpty().contains("fritekst", ignoreCase = true) -> fritekst(profil, random)

        verdi is Boolean -> when (profil) {
            Profil.TOM -> false
            Profil.FULL -> true
            Profil.TILFELDIG -> random.nextBoolean()
        }

        verdi is Int -> if (skalNulles(profil, random)) 0 else verdi

        verdi is Long -> if (skalNulles(profil, random)) 0L else verdi

        verdi is Enum<*> -> if (profil == Profil.TILFELDIG) enumverdier(parameter)?.random(random) ?: verdi else verdi

        verdi is List<*> && parameter.type.classifier == List::class -> varierListe(verdi, profil, random)

        else -> varier(verdi, profil, random)
    }
}

private fun varierListe(liste: List<*>, profil: Profil, random: Random): List<Any?> {
    if (liste.isEmpty()) return liste
    val antall = when (profil) {
        Profil.TOM -> 0
        Profil.FULL -> MANGE
        Profil.TILFELDIG -> listOf(0, 1, liste.size, MANGE).random(random)
    }
    return List(antall) { indeks -> liste[indeks % liste.size]?.let { varier(it, profil, random) } }
}

private fun fritekst(profil: Profil, random: Random): String = when (profil) {
    Profil.TOM -> ""
    Profil.FULL -> fritekster.filter { it.isNotBlank() }.joinToString("\n\n")
    Profil.TILFELDIG -> fritekster.random(random)
}

private fun skalFjernes(profil: Profil, random: Random): Boolean = when (profil) {
    Profil.TOM -> true
    Profil.FULL -> false
    Profil.TILFELDIG -> random.nextInt(3) == 0
}

private fun skalNulles(profil: Profil, random: Random): Boolean = when (profil) {
    Profil.TOM -> true
    Profil.FULL -> false
    Profil.TILFELDIG -> random.nextInt(3) == 0
}

private fun enumverdier(parameter: KParameter): Array<out Any>? =
    (parameter.type.classifier as? KClass<*>)?.java?.enumConstants
