package økonomi.domain.simulering

/**
 * Vi har denne i hovedsak med for å kunne gi saksbehandler en større forståelse av utbetalingen.
 *
 * @param beløp Beløpet som skal trekkes fra utbetalingen. Må være større enn 0.
 * @param klassekode Se i sammenheng med [klassekodeBeskrivelse]. Dette er oppdrag sitt domeneord og vi har ikke noe forhold til det annet enn å vise saksbehandler. Vi har sett verdier som AVSUINTE (Avregning SU) og BSKTKRED (Kreditor disponerer)
 * @param klassekodeBeskrivelse Se i sammenheng med [klassekode]. Dette er oppdrag sitt domeneord og vi har ikke noe forhold til det annet enn å vise saksbehandler. Vi har sett verdier som Avregning SU og Kreditor disponerer
 * @param typeSats Dette er oppdrag sitt domeneord og vi har ikke noe forhold til det annet enn å vise saksbehandler. Vi har sett verdiene LOPM og LOPP. Jah fant ikke noe dokumentasjon på hva dette betyr.
 */
data class Simuleringstrekk(
    val beløp: Int,
    val klassekode: String,
    val klassekodeBeskrivelse: String,
    val typeSats: String,
) {
    init {
        require(beløp > 0) {
            "Beløp må være større enn 0"
        }
    }
}
