package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
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
    /** En eller flere perioder kravgrunnlaget knyttes mot. Antar at det finnes minst ett element i lista. */
    val grunnlagsperioder: List<Grunnlagsperiode>,
) {

    init {
        grunnlagsperioder.map { it.periode }.let {
            require(it.sorted() == it) {
                "Kravgrunnlagsperiodene må være sortert."
            }
            it.zipWithNext { a, b ->
                require(!a.overlapper(b)) {
                    "Perioder kan ikke overlappe."
                }
            }
        }
    }

    fun forPeriode(periode: DatoIntervall): Grunnlagsperiode? {
        return grunnlagsperioder.find { it.periode == periode }
    }

    val summertBetaltSkattForYtelsesgruppen by lazy { grunnlagsperioder.sumOf { it.betaltSkattForYtelsesgruppen } }
    val summertBruttoTidligereUtbetalt by lazy { grunnlagsperioder.sumOf { it.bruttoTidligereUtbetalt } }
    val summertBruttoNyUtbetaling by lazy { grunnlagsperioder.sumOf { it.bruttoNyUtbetaling } }
    val summertBruttoFeilutbetaling by lazy { grunnlagsperioder.sumOf { it.bruttoFeilutbetaling } }
    val summertNettoFeilutbetaling by lazy { grunnlagsperioder.sumOf { it.nettoFeilutbetaling } }
    val summertSkattFeilutbetaling by lazy { grunnlagsperioder.sumOf { it.skattFeilutbetaling } }

    /**
     * Vi bruker denne til visning i domenet, men vi endrer ikke på feltene.
     * SU betaler kun ut hele kroner og denne vil derfor ikke ha desimaler, selvom oppdrag støtter dette.
     *
     * Denne har klassetype YTEL og klassekode SUUFORE
     * @param bruttoTidligereUtbetalt Må sendes videre til oppdrag. Gitt at vi tidligere har utbetalt 10k for en måned (på brukers konto) vil dette feltet inneholde 10k.
     * @param bruttoNyUtbetaling Må sendes videre til oppdrag. Gitt at vi reduserer fra utbetalte 10k til 5k, vil dette feltet være 5k. Opphører vi vil dette feltet være 0.
     * @param bruttoFeilutbetaling Dette vil være differanse mellom [bruttoTidligereUtbetalt] og [bruttoNyUtbetaling]. Usikker på hva som skjer dersom differansen er negativ eller om det kan oppstå.
     * @param skatteProsent Kommer ofte med 4 desimaler i preprod.
     *
     * @throws IllegalArgumentException dersom beløpTidligereUtbetalt-beløpNyUtbetaling != feilutbetaling
     */
    data class Grunnlagsperiode(
        val periode: DatoIntervall,
        /** Kravgrunnlaget oppgir kun total skatt som er betalt for hele ytelsesgruppen til SU. Så denne kan bare brukes som en øvre grense. */
        val betaltSkattForYtelsesgruppen: Int,
        val bruttoTidligereUtbetalt: Int,
        val bruttoNyUtbetaling: Int,
        val bruttoFeilutbetaling: Int,
        val skatteProsent: BigDecimal,
    ) {

        init {
            require(bruttoTidligereUtbetalt > 0) {
                "Forventer at kravgrunnlag.bruttoTidligereUtbetalt > 0, men var $bruttoTidligereUtbetalt"
            }
            require(bruttoNyUtbetaling >= 0) {
                "Forventer at kravgrunnlag.bruttoNyUtbetaling >= 0, men var $bruttoNyUtbetaling"
            }
            require(bruttoFeilutbetaling > 0) {
                "Forventer at kravgrunnlag.bruttoFeilutbetaling > 0, men var $bruttoFeilutbetaling"
            }
            require(skatteProsent >= BigDecimal.ZERO) {
                "Forventer at kravgrunnlag.skatteProsent >= 0, men var $skatteProsent"
            }
            require(bruttoTidligereUtbetalt - bruttoNyUtbetaling == bruttoFeilutbetaling) {
                "Forventet at bruttoTidligereUtbetalt($bruttoTidligereUtbetalt) - bruttoNyUtbetaling($bruttoNyUtbetaling) == bruttoFeilutbetaling($bruttoFeilutbetaling)"
            }
        }

        /**
         * Den delen av feilutbetalinga som er betalt til skatteetaten.
         * Vi runder opp til nærmeste hele krone, dette for at bruker og ikke nav skal få fordelen.
         * Kommentar jah: Usikker på hva som skjer dersom f.eks. både SU og Uføre krever tilbake penger fra samme periode og totalt sender en større sum enn det som er betalt til skatteetaten.
         */
        val skattFeilutbetaling: Int by lazy {
            BigDecimal(bruttoFeilutbetaling)
                .multiply(skatteProsent)
                .divide(BigDecimal("100"))
                .setScale(0, java.math.RoundingMode.UP)
                .intValueExact()
                .coerceAtMost(betaltSkattForYtelsesgruppen)
        }

        /**
         * Den delen av feilutbetalinga som er betalt til bruker.
         */
        val nettoFeilutbetaling by lazy {
            (bruttoFeilutbetaling - skattFeilutbetaling)
        }
    }
}
