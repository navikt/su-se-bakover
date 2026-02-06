package dokument.domain.distribuering

/**
 * Matcher dokdist sin distribuering av brev.
 * Dette flyter rett igjennom domenet vårt fra frontend til dokdist.
 * Tanken er å bruke dokdist sin validering i dette tilfellet.
 *
 * MERK at det er dette som bestemmer hvor brevet skal ikke selve headeren med navn i generert pdf. Dette blir stemplet på brevet fysisk.
 */
data class Distribueringsadresse(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val adresselinje3: String?,
    // jah: Her kunne vi valgt å bruke kodeverket for å validere postnummer og sted, men vi bruker dokdist sin validering for å spare tid.
    val postnummer: String,
    val poststed: String,
)
