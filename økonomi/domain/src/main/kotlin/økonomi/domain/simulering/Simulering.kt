package økonomi.domain.simulering

import arrow.core.Nel
import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.extensions.isEqualOrBefore
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.common.tid.periode.Måned
import java.time.LocalDate

/**
 * Et forsøk på å lage en enklere modell for simuleringer.
 * Der modellen er nærmere det vi trenger i SU. Uten mellomsteg.
 *
 * Data vi har valgt å utelate:
 *  - navnet (gjelderNavn i Oppdrag)
 *
 * @param fnr Fødselsnummeret på saken kan endre seg over tid, dette er fødselsnummeret vi mottok fra Oppdrag. I det vi mottar simuleringen, kan det være greit å verifisere at det stemmer med saken på det tidspunkten.
 * @param datoBeregnet Datoen simuleringen ble beregnet. Vi mottar dette fra Oppdrag. Dette er informasjon saksbehandler/attestant trenger for å vurdere om simuleringen er utdatert og bør kjøres på nytt.
 * @param nettoBeløp Totalbeløpet for hele simuleringsperioden som kommer til å bli utbetalt. Skal kunne regne dette ved å summere alle periodene i simuleringen. Totalutbetaling - skatt - trekk - tilbakeføringer (det som allerede er overført til UR).
 *
 * @property bruttoTotalUtbetaling Hvor mye en bruker egentlig skal ha eller skulle hatt utbetalt.
 * @property bruttoTilUtbetaling Hvor mye penger vi skal utbetale til bruker.
 * @property bruttoTidligereUtbetalt Hvor mye penger vi har utbetalt til bruker tidligere.
 * @property bruttoFeilutbetaling Hvor mye penger vi har utbetalt til bruker tidligere som vi ikke skulle hatt utbetalt.
 */
data class Simulering(
    val fnr: Fnr,
    val datoBeregnet: LocalDate,
    val nettoBeløp: Beløp,
    val perioder: Nel<Simuleringsperiode>,
) {
    val bruttoTotalUtbetaling = perioder.sumOf { it.bruttoTotalUtbetaling }
    val bruttoTilUtbetaling = perioder.sumOf { it.bruttoTilUtbetaling }
    val bruttoTidligereUtbetalt = perioder.sumOf { it.bruttoTidligereUtbetalt }
    val bruttoFeilutbetaling = perioder.sumOf { it.bruttoFeilutbetaling }

    init {
        require(
            perioder.all {
                when (it) {
                    is Simuleringsperiode.Feilutbetaling -> true
                    is Simuleringsperiode.TilUtbetaling -> it.skalEtterbetales() == it.forfall.isEqualOrBefore(datoBeregnet)
                    is Simuleringsperiode.Utbetalt -> true
                    is Simuleringsperiode.IngenUtbetaling -> true
                }
            },
        )
        // TODO jah: Her er det mulig å prøve å regne seg fram til nettoBeløp og sjekke at de er like. Vi vet vi må trekke fra skatt og trekk, men det kan være andre ting som påvirker ytelsen også.
    }

    fun erAllePerioderMåneder(): Boolean {
        return perioder.all { it.erMåned() }
    }

    /**
     * De tomme periodene fra oppdrag
     */
    fun erAllePerioderUtenUtbetaling(): Boolean {
        return perioder.all { it is Simuleringsperiode.IngenUtbetaling }
    }

    /**
     * Det er ingen garanti for at simuleringen starter den 1. en måned, eller slutter den siste i en måned.
     * Denne funksjonen bør ikke brukes på sikt, men er med i en overgangsfase.
     *
     * @throws IllegalArgumentException Dersom simuleringen ikke starter den 1. eller slutter den siste i en måned.
     */
//    fun periodeUnsafe(): no.nav.su.se.bakover.common.tid.periode.Periode {
//        return no.nav.su.se.bakover.common.tid.periode.Periode.create(
//            fraOgMed = tidligsteDato(),
//            tilOgMed = senesteDato(),
//        )
//    }

    fun datointervall(): DatoIntervall {
        return DatoIntervall(tidligsteDato(), senesteDato())
    }

    fun tidligsteDato(): LocalDate = perioder.minOf { it.fraOgMed }
    fun senesteDato(): LocalDate = perioder.maxOf { it.tilOgMed }

    /**
     * En simulering er delt inn i perioder, som ofte stemmer overens med fagsystemets måneder, i SU sitt tilfelle månedsperioder.
     * Dessverre er ikke dette alltid tilfelle og vi kan få trekk og andre manuelle posteringer som ikke er første/siste dagen i måneden.
     * Når dette skjer vil oppdrag re-periodisere ytelsen vår til å aligne med trekket.
     *
     * Perioder kan overlappe. Vi har observert at vi kan få en egen periode for skatt for en hel måned, mens ytelsen er delt opp i 2 deler for samme måned. Dette i forbindelse med trekk.
     *
     * Dersom en periode er tidligere utbetalt er ikke det en garanti for at bruker har pengene på konto, men pengene vil snart overføres til bank hvis utbetalingen ikke stoppes av nye utbetalingslinjer (automatisk eller manuelt).
     *
     * @property fraOgMed Datoen for første dag i perioden. Dette er ikke nødvendigvis første dag i måneden.
     * @property tilOgMed Datoen for siste dag i perioden. Dette er ikke nødvendigvis siste dag i måneden.
     * @property forfall Datoen for overføring til bank. Oppdrag overfører til UR minst 2 dager før dette. En revurdering vil ikke kunne stoppe noe som er overført til UR. Men dersom det ikke er forsent kan utbetalingen hentes tilbake fra UR manuelt.
     * @property bruttoTilUtbetaling Dersom positiv; vi skal betale penger. Dersom 0: vi skal ikke betale noe. Dersom negativ; vi skal kreve penger tilbake.
     * @property bruttoTidligereUtbetalt Må være 0 dersom vi ikke har utbetalt noe før eller positiv dersom vi har utbetalt penger tidligere.
     * @property bruttoTotalUtbetaling Hvor mye en bruker egentlig skal ha eller skulle hatt utbetalt for en gitt periode
     */
    sealed interface Simuleringsperiode {
        val datoIntervall: DatoIntervall
        val fraOgMed: LocalDate get() = datoIntervall.fraOgMed
        val tilOgMed: LocalDate get() = datoIntervall.tilOgMed
        val forfall: LocalDate?
        val bruttoTilUtbetaling: Int
        val bruttoTidligereUtbetalt: Int
        val bruttoTotalUtbetaling: Int
        val bruttoFeilutbetaling: Int

        /** Dersom perioden ikke er utbetalt, eller bare delvis utbetalt. Her har vi ikke trukket fra skatt eller trekk. */
        fun bruttoSkalUtbetales(): Boolean

        /** Dersom perioden er en hel måned. */
        fun erMåned(): Boolean = Måned.erMåned(fraOgMed, tilOgMed)

        fun datointervall(): DatoIntervall {
            return DatoIntervall(fraOgMed, tilOgMed)
        }

//        /**
//         *  Det er ingen garanti for at simuleringsperioden starter den 1. en måned, eller slutter den siste i en måned.
//         *  Denne funksjonen bør ikke brukes på sikt, men er med i en overgangsfase.
//         *
//         * @throws IllegalArgumentException Dersom simuleringsperioden ikke starter den 1. eller slutter den siste i en måned.
//         */
//        fun månedUnsafe(): Måned = Måned.fra(fraOgMed, tilOgMed)

        /**
         * Dersom false er den allerede utbetalt, eller utbetales ved et senere forfall.
         * Estimert utfra [datoBeregnet] og [forfall].
         * Kan være unøyaktig dersom datoene er nær i tid.
         * Dersom [datoBeregnet] er nært nok eller etter forfallet for en gitt periode, vil utbetalingen etterbetales (dvs. straksutbetales eller utbetales ved neste forfall basert på flagg).
         * */
        fun skalEtterbetales(): Boolean

        /** Dersom skal utbetales er negaitv */
        fun harFeilutbetaling(): Boolean

        /** Dersom vi har utbetalt penger på et tidligere tidspunkt. */
        fun harUtbetaltTidligere(): Boolean

        /**
         * Dersom vi skal utbetale penger til bruker.
         * Vi kan ha utbetalt penger før og potensielt hatt en feilutbetaling før, dette vil da være en oppjustering av stønaden.
         *
         * @param skatt Dersom det er et utestående beløp til utbetaling, forventer vi egentlig at denne skal være utfylt.
         * @param trekk Som avregning/tvungen forvaltning/annet?
         */
        data class TilUtbetaling(
            override val datoIntervall: DatoIntervall,
            override val forfall: LocalDate,
            override val bruttoTilUtbetaling: Int,
            override val bruttoTidligereUtbetalt: Int,
            private val etterbetales: Boolean,
            val skatt: Int?,
            val trekk: Simuleringstrekk?,
        ) : Simuleringsperiode {

            override val bruttoTotalUtbetaling = bruttoTidligereUtbetalt + bruttoTilUtbetaling
            override val bruttoFeilutbetaling = 0

            init {
                require(bruttoTilUtbetaling > 0) {
                    "Simuleringsperiode: Kan kun utbetale positive beløp, men var $bruttoTilUtbetaling."
                }
                require(bruttoTidligereUtbetalt >= 0) {
                    "Simuleringsperiode: tidligereUtbetalt må være 0 eller positiv, men var $bruttoTidligereUtbetalt."
                }
                require(bruttoTotalUtbetaling >= 0) {
                    "Simuleringsperiode: Sanity check på at total utbetaling er større eller lik 0, men var $bruttoTotalUtbetaling."
                }
                if (skatt != null) {
                    require(skatt >= 0) {
                        "Simuleringsperiode: skatt må være 0 eller positiv, men var $skatt."
                    }
                }
            }

            override fun bruttoSkalUtbetales() = true
            override fun skalEtterbetales() = etterbetales
            override fun harFeilutbetaling() = false
            override fun harUtbetaltTidligere() = bruttoTidligereUtbetalt > 0
        }

        /**
         * Vi har ikke en 100% garanti for at pengene er på brukers konto enda, men jo lenger før forfallet er datoBeregnet, jo større sjanse er det for at bruker har/har hatt pengene på konto.
         *
         * Vi har utbetalt penger tidligere og skal ikke betale noe mer i denne utbetalingen.
         * Dette ekskluderer ikke at det kan ha skjedd en feilutbetaling denne perioden ved en tidligere utbetaling.
         * E.g. vi har utbetalt 10k, reduserer til 5k (da ville vi fått en feilutbetaling), og oppjustert til 10k igjen og havne i denne tilstanden.
         *
         * @param trekk TODO jah: Tror ikke trekket kommer med på simuleringsmåneder som ikke er til utbetaling, men tar den med i tilfelle.
         */
        data class Utbetalt(
            override val datoIntervall: DatoIntervall,
            override val forfall: LocalDate,
            override val bruttoTidligereUtbetalt: Int,
            val trekk: Simuleringstrekk?,
        ) : Simuleringsperiode {
            override val bruttoTilUtbetaling = 0
            override val bruttoFeilutbetaling = 0
            override val bruttoTotalUtbetaling = bruttoTidligereUtbetalt

            init {
                require(bruttoTidligereUtbetalt >= 0) {
                    "Simuleringsperiode: tidligereUtbetalt må være 0 eller positiv, men var $bruttoTidligereUtbetalt."
                }
                require(bruttoTotalUtbetaling >= 0) {
                    "Simuleringsperiode: Sanity check på at total utbetaling er større eller lik 0, men var $bruttoTotalUtbetaling."
                }
            }

            override fun bruttoSkalUtbetales(): Boolean = false
            override fun skalEtterbetales() = false
            override fun harFeilutbetaling(): Boolean = false
            override fun harUtbetaltTidligere() = bruttoTidligereUtbetalt > 0
        }

        /**
         * I de tilfellene en periode aldri har vært utbetalt eller aldri skal utbetales.
         * Oppdrag hopper over disse periodene, men vi genererer de for helhetens skyld.
         */
        data class IngenUtbetaling(
            override val datoIntervall: DatoIntervall,
        ) : Simuleringsperiode {
            override val forfall = null
            override val bruttoTilUtbetaling = 0
            override val bruttoFeilutbetaling = 0
            override val bruttoTotalUtbetaling = 0
            override val bruttoTidligereUtbetalt=0

            override fun bruttoSkalUtbetales(): Boolean = false
            override fun skalEtterbetales() = false
            override fun harFeilutbetaling(): Boolean = false
            override fun harUtbetaltTidligere() = false
        }

        /**
         * @param bruttoFeilutbetaling Må være positivt
         */
        data class Feilutbetaling(
            override val datoIntervall: DatoIntervall,
            override val forfall: LocalDate,
            override val bruttoTidligereUtbetalt: Int,
            override val bruttoFeilutbetaling: Int,
        ) : Simuleringsperiode {

            override val bruttoTilUtbetaling = 0
            override val bruttoTotalUtbetaling = bruttoTidligereUtbetalt - bruttoFeilutbetaling

            init {
                require(bruttoTidligereUtbetalt >= 0) {
                    "Simuleringsperiode: tidligereUtbetalt må være 0 eller positiv, men var $bruttoTidligereUtbetalt."
                }
                require(bruttoTotalUtbetaling >= 0) {
                    "Simuleringsperiode: Sanity check på at total utbetaling er større eller lik 0, men var $bruttoTotalUtbetaling."
                }
                require(bruttoFeilutbetaling > 0) {
                    "Simuleringsperiode: Feilutbetalingsbeløp må være positivt, men var $bruttoFeilutbetaling."
                }
            }

            override fun bruttoSkalUtbetales() = bruttoTilUtbetaling > 0
            override fun skalEtterbetales() = false
            override fun harFeilutbetaling() = true

            /**
             * Siden dette er en feilutbetaling, må vi implisitt ha utbetalt noe tidligere.
             * jah: Litt usikker på om vi alltid vil kunne utrede selve beløpet i alle tilfeller her.
             */
            override fun harUtbetaltTidligere() = true
        }
    }
}
