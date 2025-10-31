package økonomi.domain.utbetaling

import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling

/**
 * Instruks som gjør det mulig å kontrollere tidspunktet eventuelle etterbetalinger skal gjennomføres på.
 * Merk at parameterert kun har effekt for tilfeller hvor det er snakk om etterbetaling, altså tilfeller hvor forfallsdato
 * for aktuelle måneder er passert. Måneder som ikke har forfalt vil utbetales i henhold til [SammenMedNestePlanlagteUtbetaling].
 */
enum class UtbetalingsinstruksjonForEtterbetalinger {
    /**
     * Angir at vi ønsker å avvente etterbetaling til neste planlagte kjøring av "normal" utbetaling. I praksis
     * vil etterbetalingen og neste normale utbetalingen skje samtidig. Brukes av f.eks regulering for å forhindre
     * at man gjennomfører en egen utbetalingsløp for etterbetaling av veldig små beløp (typisk (nyG-gammelG)*sats / 12)
     */
    SammenMedNestePlanlagteUtbetaling,

    /**
     * Angir at vi ønsker å utbetale etterbetaling ved første anledning. Forskjellen fra [SammenMedNestePlanlagteUtbetaling]
     * er at denne innstillingne fører til at prosessen med å utbetale starter med en gang oppdrag mottar utbetalingen.
     */
    SåFortSomMulig,
}
