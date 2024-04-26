package no.nav.su.se.bakover.test.utbetaling.kvittering

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.kvittering.RåUtbetalingskvitteringhendelse
import økonomi.domain.kvittering.UtbetalingskvitteringPåSakHendelse
import økonomi.domain.utbetaling.Utbetaling
import java.util.UUID

/**
 * @param utbetaling Ignoreres dersom originalKvittering er satt
 */
fun råUtbetalingskvitteringhendelse(
    hendelseId: HendelseId = HendelseId.generer(),
    hendelsestidspunkt: Tidspunkt = fixedTidspunkt,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    fnr: Fnr = Fnr.generer(),
    avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
    utbetalingId: UUID30 = UUID30.randomUUID(),
    originalKvittering: String = kvitteringXml(
        saksnummer = saksnummer,
        fnr = fnr,
        avstemmingsnøkkel = avstemmingsnøkkel,
        utbetalingId = utbetalingId,
    ),
): RåUtbetalingskvitteringhendelse {
    return RåUtbetalingskvitteringhendelse(
        hendelseId = hendelseId,
        hendelsestidspunkt = hendelsestidspunkt,
        originalKvittering = originalKvittering,
    )
}

fun utbetalingskvitteringPåSakHendelse(
    hendelseId: HendelseId = HendelseId.generer(),
    versjon: Hendelsesversjon = Hendelsesversjon.ny(),
    sakId: UUID = UUID.randomUUID(),
    hendelsestidspunkt: Tidspunkt = fixedTidspunkt,
    tidligereHendelseId: HendelseId = HendelseId.generer(),
    utbetalingsstatus: Kvittering.Utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
    utbetalingId: UUID30 = UUID30.randomUUID(),
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    fnr: Fnr = Fnr.generer(),
    avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
    originalKvittering: String = kvitteringXml(
        saksnummer = saksnummer,
        fnr = fnr,
        avstemmingsnøkkel = avstemmingsnøkkel,
        utbetalingId = utbetalingId,
    ),
): UtbetalingskvitteringPåSakHendelse {
    return UtbetalingskvitteringPåSakHendelse(
        hendelseId = hendelseId,
        versjon = versjon,
        sakId = sakId,
        hendelsestidspunkt = hendelsestidspunkt,
        tidligereHendelseId = tidligereHendelseId,
        utbetalingsstatus = utbetalingsstatus,
        originalKvittering = originalKvittering,
        utbetalingId = utbetalingId,
    )
}

fun kvitteringXml(
    utbetaling: Utbetaling = oversendtUtbetalingUtenKvittering(),
): String {
    return kvitteringXml(
        saksnummer = utbetaling.saksnummer,
        fnr = utbetaling.fnr,
        avstemmingsnøkkel = utbetaling.avstemmingsnøkkel,
        utbetalingId = utbetaling.id,
    )
}

/**
 * Kopiert fra økonomi.infrastructure.kvittering.consumer.lokal.LokalKvitteringService
 * TODO jah: Kunne gjenbrukt denne dersom den lokale jobben hadde ligget i test.
 */
fun kvitteringXml(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    fnr: Fnr = Fnr.generer(),
    avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
    utbetalingId: UUID30 = UUID30.randomUUID(),
): String {
    @Suppress("HttpUrlsUsage")
    //language=XML
    return """
<?xml version="1.0" encoding="UTF-8"?>
    <oppdrag xmlns="http://www.trygdeetaten.no/skjema/oppdrag">
       <mmel>
          <systemId>231-OPPD</systemId>
          <alvorlighetsgrad>00</alvorlighetsgrad>
       </mmel>
       <oppdrag-110>
          <kodeAksjon>1</kodeAksjon>
          <kodeEndring>NY</kodeEndring>
          <kodeFagomraade>SUUFORE</kodeFagomraade>
          <fagsystemId>$saksnummer</fagsystemId>
          <utbetFrekvens>MND</utbetFrekvens>
          <oppdragGjelderId>$fnr</oppdragGjelderId>
          <datoOppdragGjelderFom>1970-01-01</datoOppdragGjelderFom>
          <saksbehId>SU</saksbehId>
          <avstemming-115>
             <kodeKomponent>SU</kodeKomponent>
             <nokkelAvstemming>$avstemmingsnøkkel</nokkelAvstemming>
             <tidspktMelding>${avstemmingsnøkkel.opprettet}</tidspktMelding>
          </avstemming-115>
          <oppdrags-enhet-120>
             <typeEnhet>BOS</typeEnhet>
             <enhet>8020</enhet>
             <datoEnhetFom>1970-01-01</datoEnhetFom>
          </oppdrags-enhet-120>
          <oppdrags-linje-150>
             <kodeEndringLinje>NY</kodeEndringLinje>
             <delytelseId>4fad33a7-9a7d-4732-9d3f-b9d0fc</delytelseId>
             <kodeKlassifik>SUUFORE</kodeKlassifik>
             <datoVedtakFom>2020-01-01</datoVedtakFom>
             <datoVedtakTom>2020-12-31</datoVedtakTom>
             <sats>20637</sats>
             <fradragTillegg>T</fradragTillegg>
             <typeSats>MND</typeSats>
             <brukKjoreplan>N</brukKjoreplan>
             <saksbehId>SU</saksbehId>
             <utbetalesTilId>$fnr</utbetalesTilId>
             <attestant-180>
                <attestantId>A123456</attestantId>
             </attestant-180>
             <henvisning>$utbetalingId</henvisning>
             <ukjentFeltBørIgnorereres>ukjent</ukjentFeltBørIgnorereres>
          </oppdrags-linje-150>
       </oppdrag-110>
    </Oppdrag>
    """.trimIndent()
}
