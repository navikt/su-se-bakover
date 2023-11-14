package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import java.math.BigDecimal

data class Kravgrunnlag(
    /** Se [tilbakekreving.domain.kravgrunnlag.KravgrunnlagDetaljerPåSakHendelse]*/
    val hendelseId: HendelseId,

    val saksnummer: Saksnummer,
    /** Dette er en ekstern id som genereres og eies av Oppdrag. Den er transient i vårt system. Vi har observert at denne er lik for NY og ENDR, selv når vi fikk nye måneder i detaljene. Kanskje den endrer seg dersom kravgrunnlaget endrer seg vesentlig, f. eks. dersom eksisterende måneder endrer seg? */
    val eksternKravgrunnlagId: String,

    /** Dette er en ekstern id som genereres og eies av Oppdrag. Den er transient i vårt system. Kan virke som den holder seg lik helt til kravgrunnlaget er avsluttet. */
    val eksternVedtakId: String,

    /** Denne er generert av Oppdrag og er vedlagt i kravgrunnlaget, den er transient i vårt system, men brukes for å utlede eksternTidspunkt. Kan være på formatet: 2023-09-19-10.01.03.842916.*/
    val eksternKontrollfelt: String,

    /** Formatert [eksternKontrollfelt], kan brukes til å sortere hendelsene på en sak. */
    val eksternTidspunkt: Tidspunkt,

    val status: Kravgrunnlagstatus,

    /**
     * Saksbehandleren/Attestanten knyttet til vedtaket/utbetalinga.
     * Utbetalinga vår kaller dette behandler, så vi gjenbruker det her.
     * Oppdrag har ikke skillet mellom saksbehandler/attestant (men bruker ofte ordet saksbehandler).
     */
    val behandler: String,
    /**  Mappes fra referansefeltet i kravgrunnlaget. En referanse til utbetalingId (vår) som førte til opprettelse/endring av dette kravgrunnlaget. Usikker på om denne kan være null dersom det var en manuell endring som førte til opprettelse av kravgrunnlaget. */
    val utbetalingId: UUID30,
    /** En eller flere måneder kravgrunnlaget knyttes mot. Antar at det finnes minst ett element i lista. */
    val grunnlagsmåneder: List<Grunnlagsmåned>,
) {
    fun hentBeløpSkalTilbakekreves(): Månedsbeløp {
        return Månedsbeløp(
            grunnlagsmåneder
                .map { it.hentBeløpSkalTilbakekreves() }
                .filter { it.sum() > 0 },
        )
    }

    data class Grunnlagsmåned(
        val måned: Måned,
        /** Kravgrunnlaget oppgir kun total skatt som er betalt for hele ytelsesgruppen til SU. Så denne kan bare brukes som en øvre grense. */
        val betaltSkattForYtelsesgruppen: BigDecimal,
        val ytelse: Ytelse,
        val feilutbetaling: Feilutbetaling,
    ) {

        fun hentBeløpSkalTilbakekreves(): MånedBeløp {
            return MånedBeløp(
                periode = måned,
                beløp = Beløp(ytelse.beløpSkalTilbakekreves),
            )
        }

        /**
         * Vi bruker denne til visning i domenet, men vi endrer ikke på feltene.
         * SU betaler kun ut hele kroner og denne vil derfor ikke ha desimaler, selvom oppdrag støtter dette.
         *
         * Denne har klassetype YTEL og klassekode SUUFORE
         * @param beløpTidligereUtbetaling Gitt at vi tidligere har utbetalt 10k for en måned (på brukers konto) vil dette feltet inneholde 10k.
         * @param beløpNyUtbetaling Gitt at vi reduserer fra utbetalte 10k til 5k, vil dette feltet være 5k. Opphører vi vil dette feltet være 0.
         * @param beløpSkalTilbakekreves Dette vil være differanse mellom [beløpTidligereUtbetaling] og [beløpNyUtbetaling]. Usikker på hva som skjer dersom differansen er negativ eller om det kan oppstå.
         * @param beløpSkalIkkeTilbakekreves Dette feltet er transient. Vi må sende det Antar at denne alltid er 0. Hvis denne er større enn 0, har oppdrag bestemt at vi ikke skal tilbakekreve denne delen, men siden vi ikke har konfigurert noen brøk eller tilbakekrevingsregler i oppdrag.
         * @param skatteProsent Kommer ofte med 4 desimaler i preprod.
         *
         * Summen av [beløpSkalTilbakekreves] og [beløpSkalIkkeTilbakekreves] er lik differansen mellom [beløpTidligereUtbetaling] og [beløpNyUtbetaling].
         */
        data class Ytelse(
            val beløpTidligereUtbetaling: Int,
            val beløpNyUtbetaling: Int,
            val beløpSkalTilbakekreves: Int,
            val beløpSkalIkkeTilbakekreves: Int,
            val skatteProsent: BigDecimal,
        ) {
            init {
                // TODO jah: Det er også mulig å legge inn noen forventninger på tvers av feltene
                require(beløpTidligereUtbetaling > 0) {
                    "Forventer at kravgrunnlag.beløpTidligereUtbetaling > 0, men var $beløpTidligereUtbetaling"
                }
                require(beløpNyUtbetaling >= 0) {
                    "Forventer at kravgrunnlag.beløpNyUtbetaling >= 0, men var $beløpNyUtbetaling"
                }
                require(beløpSkalTilbakekreves >= 0) {
                    "Forventer at kravgrunnlag.beløpSkalTilbakekreves >= 0, men var $beløpSkalTilbakekreves"
                }
                require(beløpSkalIkkeTilbakekreves == 0) {
                    "Forventer at kravgrunnlag.beløpSkalIkkeTilbakekreves == 0, men var $beløpSkalIkkeTilbakekreves"
                }
                require(skatteProsent >= BigDecimal.ZERO) {
                    "Forventer at kravgrunnlag.skatteProsent >= 0, men var $skatteProsent"
                }
            }
        }

        /**
         * Hele denne typen er transient. Dvs. at vi ikke bruker den i domenet, men sender den rått videre til tilbakekrevingskomponenten.
         * SU betaler kun ut hele kroner og denne vil derfor ikke ha desimaler, selvom oppdrag støtter dette.
         *
         * Denne har klassetype FEIL og klassekode KL_KODE_FEIL_INT
         *
         * @param beløpTidligereUtbetaling Antar at den alltid kommer som 0.00, men vi beholder den som transient, siden vi må sende den til tilbakekrevingskomponenten.
         * @param beløpNyUtbetaling Antar at det kun er denne som er utfylt for klassetype FEIL.
         * @param beløpSkalTilbakekreves Antar at den alltid kommer som 0.00, men vi beholder den som transient, siden vi må sende den til tilbakekrevingskomponenten.
         * @param beløpSkalIkkeTilbakekreves Antar at den alltid kommer som 0.00, men vi beholder den som transient, siden vi må sende den til tilbakekrevingskomponenten.
         */
        data class Feilutbetaling(
            val beløpTidligereUtbetaling: Int,
            val beløpNyUtbetaling: Int,
            val beløpSkalTilbakekreves: Int,
            val beløpSkalIkkeTilbakekreves: Int,
        )
    }
}
