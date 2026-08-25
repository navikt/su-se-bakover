package no.nav.su.se.bakover.common.domain.job

/**
 * Sentral oversikt over alle kjente jobbnavn med beskrivelse. Brukes av jobbene selv og av tester/testdata.
 */
enum class JobbNavn(val visningsnavn: String, val beskrivelse: String) {
    JOURNALFØR_DOKUMENTER("Journalfør dokumenter", "Journalfører dokumenter (brev, skattedokumenter) mot Joark."),
    BESTILL_BREVDISTRIBUSJON("Bestill brevdistribusjon", "Bestiller fysisk/digital distribusjon av journalførte brev."),
    GRENSESNITTSAVSTEMMING("GrensesnittsavstemingJob", "Daglig grensesnittsavstemming mot Oppdragssystemet (OS)."),
    KONSISTENSAVSTEMMING("KonsistensavstemmingJob", "Konsistensavstemming som verifiserer at utbetalinger stemmer med OS."),
    KLAGEINSTANSHENDELSE("Håndter utfall fra Klageinstans", "Henter og prosesserer vedtak fra Klageinstans (KA)."),
    PERSONHENDELSE_OPPGAVE("Opprett personhendelse oppgaver", "Oppretter oppgaver i Gosys basert på personhendelser fra PDL."),
    PERSONHENDELSE_AUTOMATISK("Automatisk behandling av personhendelser", "Behandler personhendelser automatisk (dødsfall, utflytting, etc.)."),
    KONTROLLSAMTALEINNKALLING("Utsendelse av kontrollsamtaleinnkallelser", "Sender innkalling til kontrollsamtale for løpende stønadsperioder."),
    TILBAKEKREVING("Tilbakekreving", "Prosesserer tilbakekrevingsvedtak, forhåndsvarsler og kravgrunnlag."),
    DOKUMENT("Dokument", "Hendelsesdrevne dokumentjobber (journalføring og distribusjon)."),
    SEND_PÅMINNELSE_NY_STØNADSPERIODE("SendPåminnelseNyStønadsperiodeJob", "Sender påminnelse til brukere om å søke ny stønadsperiode."),
    STANS_YTELSE_MANGLENDE_OPPMØTE("StansYtelseVedManglendeOppmøteTilKontrollsamtaleJob", "Stanser ytelse for brukere som ikke møtte til kontrollsamtale."),
    LAG_STØNADSTATISTIKK_FOR_MÅNED("LagStønadstatistikkForMånedJob", "Genererer månedlig stønadstatistikk for rapportering."),
    FORSØK_JOURNALFØRING_KONTROLLNOTAT("ForsøkJournalføringKontrollnotatJob", "Retryer journalføring av kontrollnotater som feilet."),
    FSS_PROXY("FssProxyJob", "Holder FSS-proxy varm for å unngå cold-start-latens."),
    RETRY_IVERKSETT_REGULERING("RetryIverksettReguleringJob", "Retryer iverksetting av reguleringer som feilet mot OS."),
    SAKSTATISTIKK_TIL_BIGQUERY("SakstatistikkTilBigQuery", "Sender sakstatistikk til BigQuery for analyse."),
    SØKNAD_STATISTIKK("SøknadStatistikk", "Sender søknadsstatistikk til statistikkløsningen."),
    FRITEKST_AVSLAG("FritekstAvslagJobb", "Prosesserer fritekstavslag-statistikk."),
    FRADRAGSSJEKKEN("FradragsSjekkenJob", "Sjekker fradragsdata mot eksterne kilder for konsistens."),
    FIKS_SØKNADER_UTEN_OPPGAVE("FiksSøknaderUtenOppgave", "Oppretter manglende oppgaver for søknader som mangler dette."),
    INSTITUSJONSOPPHOLD_OPPGAVE("Institusjonsopphold-hendelse oppgave", "Oppretter oppgaver basert på institusjonsopphold-hendelser."),
    KVITTERINGSHENDELSER("KvitteringshendelserJobb", "Prosesserer utbetalingskvitteringer fra OS."),
    LOKAL_MOTTA_KRAVGRUNNLAG("local-motta-kravgrunnlag", "Mottar kravgrunnlag fra OS via IBM MQ (kun lokalt)."),
    ;

    companion object {
        fun fraVisningsnavn(visningsnavn: String): JobbNavn? = entries.firstOrNull { it.visningsnavn == visningsnavn }
    }
}
