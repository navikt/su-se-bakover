package no.nav.su.se.bakover.domain.kontrollnotat.kontrollnotatInnhold

data class KontrollnotatInnhold(
    val personligOppmøte: Boolean,
    val fullmaktOgLegeerklæring: Boolean?,
    val originalPass: Boolean,
    val gyldigPass: Boolean,
    val harVærtUtenlands: Boolean,
    val utenlandsoppholdDatoer: List<String>,
    val harPlanerOmUtenlandsreise: Boolean,
    val planlagteUtenlandsreiseDatoer: List<String>,
    val reiseDokumentasjon: Boolean,
    val økonomiskSituasjon: Boolean,
    val andreForhold: Boolean,
    val skatteOpplysninger: Boolean,
    val fritekst: String?,
)
