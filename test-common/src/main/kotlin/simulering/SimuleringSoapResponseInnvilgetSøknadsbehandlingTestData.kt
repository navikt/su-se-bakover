@file:Suppress("HttpUrlsUsage")

package no.nav.su.se.bakover.test.simulering

/**
 * Hentet fra preprod, saksnummer 10002227
 * Innvilget søknadsbehandling.
 * Beregnet/simulert tidspunkt: 2024-02-07
 * Stønadsperiode: 01.01.2023 - 31.12.2023
 * Uføregrad: 10%, forventet inntekt etter uførhet 10,-
 * Etterbetaling alle måneder.
 * Sats jan-april: 23038 (23039,00 - 0,83)
 * Sats mai-des: 24514 (24515,00 - 0,83)
 */
fun simuleringSoapResponseInnvilgetSøknadsbehandling(): String {
    return """
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <SOAP-ENV:Body>
    <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
      <response xmlns="">
        <simulering>
          <gjelderId>12345678901</gjelderId>
          <gjelderNavn>TEST TESTESEN</gjelderNavn>
          <datoBeregnet>2024-02-07</datoBeregnet>
          <kodeFaggruppe>INNT</kodeFaggruppe>
          <belop>161432.00</belop>
          <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
            <periodeFom xmlns="">2023-01-01</periodeFom>
            <periodeTom xmlns="">2023-01-31</periodeTom>
            <beregningStoppnivaa>
              <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
              <stoppNivaaId xmlns="">1</stoppNivaaId>
              <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
              <oppdragsId xmlns="">70450237</oppdragsId>
              <fagsystemId xmlns="">10002227</fagsystemId>
              <kid xmlns="" />
              <utbetalesTilId xmlns="">12345678901</utbetalesTilId>
              <utbetalesTilNavn xmlns="">SJOKOLADEKAKE BEMERKELSES</utbetalesTilNavn>
              <bilagsType xmlns="">U</bilagsType>
              <forfall xmlns="">2024-02-07</forfall>
              <feilkonto xmlns="">false</feilkonto>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-01-01</faktiskFom>
                <faktiskTom xmlns="">2023-01-31</faktiskTom>
                <kontoStreng xmlns="">0510000</kontoStreng>
                <behandlingskode xmlns="">0</behandlingskode>
                <belop xmlns="">-10136.00</belop>
                <trekkVedtakId xmlns="">13002940</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">0</linjeId>
                <sats xmlns="">0.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">31.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">0</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="" />
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">FSKTSKAT</klassekode>
                <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">SKAT</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-01-01</faktiskFom>
                <faktiskTom xmlns="">2023-01-31</faktiskTom>
                <kontoStreng xmlns="">4952000</kontoStreng>
                <behandlingskode xmlns="">2</behandlingskode>
                <belop xmlns="">23038.00</belop>
                <trekkVedtakId xmlns="">0</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">1</linjeId>
                <sats xmlns="">23038.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">1.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">10</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="">e24085ca-4ab4-4b2c-b48c-3b30b4</delytelseId>
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">SUUFORE</klassekode>
                <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">YTEL</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
            </beregningStoppnivaa>
          </beregningsPeriode>
          <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
            <periodeFom xmlns="">2023-02-01</periodeFom>
            <periodeTom xmlns="">2023-02-28</periodeTom>
            <beregningStoppnivaa>
              <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
              <stoppNivaaId xmlns="">2</stoppNivaaId>
              <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
              <oppdragsId xmlns="">70450237</oppdragsId>
              <fagsystemId xmlns="">10002227</fagsystemId>
              <kid xmlns="" />
              <utbetalesTilId xmlns="">12345678901</utbetalesTilId>
              <utbetalesTilNavn xmlns="">SJOKOLADEKAKE BEMERKELSES</utbetalesTilNavn>
              <bilagsType xmlns="">U</bilagsType>
              <forfall xmlns="">2024-02-07</forfall>
              <feilkonto xmlns="">false</feilkonto>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-02-01</faktiskFom>
                <faktiskTom xmlns="">2023-02-28</faktiskTom>
                <kontoStreng xmlns="">0510000</kontoStreng>
                <behandlingskode xmlns="">0</behandlingskode>
                <belop xmlns="">-10136.00</belop>
                <trekkVedtakId xmlns="">13002940</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">0</linjeId>
                <sats xmlns="">0.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">28.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">0</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="" />
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">FSKTSKAT</klassekode>
                <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">SKAT</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-02-01</faktiskFom>
                <faktiskTom xmlns="">2023-02-28</faktiskTom>
                <kontoStreng xmlns="">4952000</kontoStreng>
                <behandlingskode xmlns="">2</behandlingskode>
                <belop xmlns="">23038.00</belop>
                <trekkVedtakId xmlns="">0</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">1</linjeId>
                <sats xmlns="">23038.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">1.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">10</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="">e24085ca-4ab4-4b2c-b48c-3b30b4</delytelseId>
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">SUUFORE</klassekode>
                <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">YTEL</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
            </beregningStoppnivaa>
          </beregningsPeriode>
          <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
            <periodeFom xmlns="">2023-03-01</periodeFom>
            <periodeTom xmlns="">2023-03-31</periodeTom>
            <beregningStoppnivaa>
              <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
              <stoppNivaaId xmlns="">3</stoppNivaaId>
              <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
              <oppdragsId xmlns="">70450237</oppdragsId>
              <fagsystemId xmlns="">10002227</fagsystemId>
              <kid xmlns="" />
              <utbetalesTilId xmlns="">12345678901</utbetalesTilId>
              <utbetalesTilNavn xmlns="">SJOKOLADEKAKE BEMERKELSES</utbetalesTilNavn>
              <bilagsType xmlns="">U</bilagsType>
              <forfall xmlns="">2024-02-07</forfall>
              <feilkonto xmlns="">false</feilkonto>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-03-01</faktiskFom>
                <faktiskTom xmlns="">2023-03-31</faktiskTom>
                <kontoStreng xmlns="">0510000</kontoStreng>
                <behandlingskode xmlns="">0</behandlingskode>
                <belop xmlns="">-10136.00</belop>
                <trekkVedtakId xmlns="">13002940</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">0</linjeId>
                <sats xmlns="">0.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">31.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">0</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="" />
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">FSKTSKAT</klassekode>
                <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">SKAT</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-03-01</faktiskFom>
                <faktiskTom xmlns="">2023-03-31</faktiskTom>
                <kontoStreng xmlns="">4952000</kontoStreng>
                <behandlingskode xmlns="">2</behandlingskode>
                <belop xmlns="">23038.00</belop>
                <trekkVedtakId xmlns="">0</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">1</linjeId>
                <sats xmlns="">23038.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">1.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">10</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="">e24085ca-4ab4-4b2c-b48c-3b30b4</delytelseId>
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">SUUFORE</klassekode>
                <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">YTEL</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
            </beregningStoppnivaa>
          </beregningsPeriode>
          <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
            <periodeFom xmlns="">2023-04-01</periodeFom>
            <periodeTom xmlns="">2023-04-30</periodeTom>
            <beregningStoppnivaa>
              <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
              <stoppNivaaId xmlns="">4</stoppNivaaId>
              <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
              <oppdragsId xmlns="">70450237</oppdragsId>
              <fagsystemId xmlns="">10002227</fagsystemId>
              <kid xmlns="" />
              <utbetalesTilId xmlns="">12345678901</utbetalesTilId>
              <utbetalesTilNavn xmlns="">SJOKOLADEKAKE BEMERKELSES</utbetalesTilNavn>
              <bilagsType xmlns="">U</bilagsType>
              <forfall xmlns="">2024-02-07</forfall>
              <feilkonto xmlns="">false</feilkonto>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-04-01</faktiskFom>
                <faktiskTom xmlns="">2023-04-30</faktiskTom>
                <kontoStreng xmlns="">0510000</kontoStreng>
                <behandlingskode xmlns="">0</behandlingskode>
                <belop xmlns="">-10136.00</belop>
                <trekkVedtakId xmlns="">13002940</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">0</linjeId>
                <sats xmlns="">0.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">30.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">0</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="" />
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">FSKTSKAT</klassekode>
                <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">SKAT</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-04-01</faktiskFom>
                <faktiskTom xmlns="">2023-04-30</faktiskTom>
                <kontoStreng xmlns="">4952000</kontoStreng>
                <behandlingskode xmlns="">2</behandlingskode>
                <belop xmlns="">23038.00</belop>
                <trekkVedtakId xmlns="">0</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">1</linjeId>
                <sats xmlns="">23038.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">1.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">10</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="">e24085ca-4ab4-4b2c-b48c-3b30b4</delytelseId>
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">SUUFORE</klassekode>
                <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">YTEL</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
            </beregningStoppnivaa>
          </beregningsPeriode>
          <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
            <periodeFom xmlns="">2023-05-01</periodeFom>
            <periodeTom xmlns="">2023-05-31</periodeTom>
            <beregningStoppnivaa>
              <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
              <stoppNivaaId xmlns="">5</stoppNivaaId>
              <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
              <oppdragsId xmlns="">70450237</oppdragsId>
              <fagsystemId xmlns="">10002227</fagsystemId>
              <kid xmlns="" />
              <utbetalesTilId xmlns="">12345678901</utbetalesTilId>
              <utbetalesTilNavn xmlns="">SJOKOLADEKAKE BEMERKELSES</utbetalesTilNavn>
              <bilagsType xmlns="">U</bilagsType>
              <forfall xmlns="">2024-02-07</forfall>
              <feilkonto xmlns="">false</feilkonto>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-05-01</faktiskFom>
                <faktiskTom xmlns="">2023-05-31</faktiskTom>
                <kontoStreng xmlns="">0510000</kontoStreng>
                <behandlingskode xmlns="">0</behandlingskode>
                <belop xmlns="">-10786.00</belop>
                <trekkVedtakId xmlns="">13002940</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">0</linjeId>
                <sats xmlns="">0.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">31.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">0</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="" />
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">FSKTSKAT</klassekode>
                <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">SKAT</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-05-01</faktiskFom>
                <faktiskTom xmlns="">2023-05-31</faktiskTom>
                <kontoStreng xmlns="">4952000</kontoStreng>
                <behandlingskode xmlns="">2</behandlingskode>
                <belop xmlns="">24514.00</belop>
                <trekkVedtakId xmlns="">0</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">2</linjeId>
                <sats xmlns="">24514.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">1.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">10</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="">fcce6916-5c7b-4ad3-86b5-fac667</delytelseId>
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">SUUFORE</klassekode>
                <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">YTEL</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
            </beregningStoppnivaa>
          </beregningsPeriode>
          <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
            <periodeFom xmlns="">2023-06-01</periodeFom>
            <periodeTom xmlns="">2023-06-30</periodeTom>
            <beregningStoppnivaa>
              <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
              <stoppNivaaId xmlns="">6</stoppNivaaId>
              <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
              <oppdragsId xmlns="">70450237</oppdragsId>
              <fagsystemId xmlns="">10002227</fagsystemId>
              <kid xmlns="" />
              <utbetalesTilId xmlns="">12345678901</utbetalesTilId>
              <utbetalesTilNavn xmlns="">SJOKOLADEKAKE BEMERKELSES</utbetalesTilNavn>
              <bilagsType xmlns="">U</bilagsType>
              <forfall xmlns="">2024-02-07</forfall>
              <feilkonto xmlns="">false</feilkonto>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-06-01</faktiskFom>
                <faktiskTom xmlns="">2023-06-30</faktiskTom>
                <kontoStreng xmlns="">0510000</kontoStreng>
                <behandlingskode xmlns="">0</behandlingskode>
                <belop xmlns="">-10786.00</belop>
                <trekkVedtakId xmlns="">13002940</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">0</linjeId>
                <sats xmlns="">0.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">30.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">0</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="" />
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">FSKTSKAT</klassekode>
                <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">SKAT</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-06-01</faktiskFom>
                <faktiskTom xmlns="">2023-06-30</faktiskTom>
                <kontoStreng xmlns="">4952000</kontoStreng>
                <behandlingskode xmlns="">2</behandlingskode>
                <belop xmlns="">24514.00</belop>
                <trekkVedtakId xmlns="">0</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">2</linjeId>
                <sats xmlns="">24514.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">1.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">10</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="">fcce6916-5c7b-4ad3-86b5-fac667</delytelseId>
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">SUUFORE</klassekode>
                <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">YTEL</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
            </beregningStoppnivaa>
          </beregningsPeriode>
          <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
            <periodeFom xmlns="">2023-07-01</periodeFom>
            <periodeTom xmlns="">2023-07-31</periodeTom>
            <beregningStoppnivaa>
              <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
              <stoppNivaaId xmlns="">7</stoppNivaaId>
              <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
              <oppdragsId xmlns="">70450237</oppdragsId>
              <fagsystemId xmlns="">10002227</fagsystemId>
              <kid xmlns="" />
              <utbetalesTilId xmlns="">12345678901</utbetalesTilId>
              <utbetalesTilNavn xmlns="">SJOKOLADEKAKE BEMERKELSES</utbetalesTilNavn>
              <bilagsType xmlns="">U</bilagsType>
              <forfall xmlns="">2024-02-07</forfall>
              <feilkonto xmlns="">false</feilkonto>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-07-01</faktiskFom>
                <faktiskTom xmlns="">2023-07-31</faktiskTom>
                <kontoStreng xmlns="">0510000</kontoStreng>
                <behandlingskode xmlns="">0</behandlingskode>
                <belop xmlns="">-10786.00</belop>
                <trekkVedtakId xmlns="">13002940</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">0</linjeId>
                <sats xmlns="">0.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">31.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">0</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="" />
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">FSKTSKAT</klassekode>
                <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">SKAT</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-07-01</faktiskFom>
                <faktiskTom xmlns="">2023-07-31</faktiskTom>
                <kontoStreng xmlns="">4952000</kontoStreng>
                <behandlingskode xmlns="">2</behandlingskode>
                <belop xmlns="">24514.00</belop>
                <trekkVedtakId xmlns="">0</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">2</linjeId>
                <sats xmlns="">24514.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">1.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">10</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="">fcce6916-5c7b-4ad3-86b5-fac667</delytelseId>
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">SUUFORE</klassekode>
                <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">YTEL</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
            </beregningStoppnivaa>
          </beregningsPeriode>
          <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
            <periodeFom xmlns="">2023-08-01</periodeFom>
            <periodeTom xmlns="">2023-08-31</periodeTom>
            <beregningStoppnivaa>
              <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
              <stoppNivaaId xmlns="">8</stoppNivaaId>
              <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
              <oppdragsId xmlns="">70450237</oppdragsId>
              <fagsystemId xmlns="">10002227</fagsystemId>
              <kid xmlns="" />
              <utbetalesTilId xmlns="">12345678901</utbetalesTilId>
              <utbetalesTilNavn xmlns="">SJOKOLADEKAKE BEMERKELSES</utbetalesTilNavn>
              <bilagsType xmlns="">U</bilagsType>
              <forfall xmlns="">2024-02-07</forfall>
              <feilkonto xmlns="">false</feilkonto>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-08-01</faktiskFom>
                <faktiskTom xmlns="">2023-08-31</faktiskTom>
                <kontoStreng xmlns="">0510000</kontoStreng>
                <behandlingskode xmlns="">0</behandlingskode>
                <belop xmlns="">-10786.00</belop>
                <trekkVedtakId xmlns="">13002940</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">0</linjeId>
                <sats xmlns="">0.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">31.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">0</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="" />
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">FSKTSKAT</klassekode>
                <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">SKAT</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-08-01</faktiskFom>
                <faktiskTom xmlns="">2023-08-31</faktiskTom>
                <kontoStreng xmlns="">4952000</kontoStreng>
                <behandlingskode xmlns="">2</behandlingskode>
                <belop xmlns="">24514.00</belop>
                <trekkVedtakId xmlns="">0</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">2</linjeId>
                <sats xmlns="">24514.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">1.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">10</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="">fcce6916-5c7b-4ad3-86b5-fac667</delytelseId>
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">SUUFORE</klassekode>
                <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">YTEL</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
            </beregningStoppnivaa>
          </beregningsPeriode>
          <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
            <periodeFom xmlns="">2023-09-01</periodeFom>
            <periodeTom xmlns="">2023-09-30</periodeTom>
            <beregningStoppnivaa>
              <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
              <stoppNivaaId xmlns="">9</stoppNivaaId>
              <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
              <oppdragsId xmlns="">70450237</oppdragsId>
              <fagsystemId xmlns="">10002227</fagsystemId>
              <kid xmlns="" />
              <utbetalesTilId xmlns="">12345678901</utbetalesTilId>
              <utbetalesTilNavn xmlns="">SJOKOLADEKAKE BEMERKELSES</utbetalesTilNavn>
              <bilagsType xmlns="">U</bilagsType>
              <forfall xmlns="">2024-02-07</forfall>
              <feilkonto xmlns="">false</feilkonto>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-09-01</faktiskFom>
                <faktiskTom xmlns="">2023-09-30</faktiskTom>
                <kontoStreng xmlns="">0510000</kontoStreng>
                <behandlingskode xmlns="">0</behandlingskode>
                <belop xmlns="">-10786.00</belop>
                <trekkVedtakId xmlns="">13002940</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">0</linjeId>
                <sats xmlns="">0.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">30.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">0</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="" />
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">FSKTSKAT</klassekode>
                <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">SKAT</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-09-01</faktiskFom>
                <faktiskTom xmlns="">2023-09-30</faktiskTom>
                <kontoStreng xmlns="">4952000</kontoStreng>
                <behandlingskode xmlns="">2</behandlingskode>
                <belop xmlns="">24514.00</belop>
                <trekkVedtakId xmlns="">0</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">2</linjeId>
                <sats xmlns="">24514.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">1.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">10</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="">fcce6916-5c7b-4ad3-86b5-fac667</delytelseId>
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">SUUFORE</klassekode>
                <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">YTEL</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
            </beregningStoppnivaa>
          </beregningsPeriode>
          <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
            <periodeFom xmlns="">2023-10-01</periodeFom>
            <periodeTom xmlns="">2023-10-31</periodeTom>
            <beregningStoppnivaa>
              <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
              <stoppNivaaId xmlns="">10</stoppNivaaId>
              <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
              <oppdragsId xmlns="">70450237</oppdragsId>
              <fagsystemId xmlns="">10002227</fagsystemId>
              <kid xmlns="" />
              <utbetalesTilId xmlns="">12345678901</utbetalesTilId>
              <utbetalesTilNavn xmlns="">SJOKOLADEKAKE BEMERKELSES</utbetalesTilNavn>
              <bilagsType xmlns="">U</bilagsType>
              <forfall xmlns="">2024-02-07</forfall>
              <feilkonto xmlns="">false</feilkonto>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-10-01</faktiskFom>
                <faktiskTom xmlns="">2023-10-31</faktiskTom>
                <kontoStreng xmlns="">0510000</kontoStreng>
                <behandlingskode xmlns="">0</behandlingskode>
                <belop xmlns="">-10786.00</belop>
                <trekkVedtakId xmlns="">13002940</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">0</linjeId>
                <sats xmlns="">0.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">31.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">0</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="" />
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">FSKTSKAT</klassekode>
                <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">SKAT</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-10-01</faktiskFom>
                <faktiskTom xmlns="">2023-10-31</faktiskTom>
                <kontoStreng xmlns="">4952000</kontoStreng>
                <behandlingskode xmlns="">2</behandlingskode>
                <belop xmlns="">24514.00</belop>
                <trekkVedtakId xmlns="">0</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">2</linjeId>
                <sats xmlns="">24514.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">1.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">10</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="">fcce6916-5c7b-4ad3-86b5-fac667</delytelseId>
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">SUUFORE</klassekode>
                <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">YTEL</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
            </beregningStoppnivaa>
          </beregningsPeriode>
          <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
            <periodeFom xmlns="">2023-11-01</periodeFom>
            <periodeTom xmlns="">2023-11-30</periodeTom>
            <beregningStoppnivaa>
              <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
              <stoppNivaaId xmlns="">11</stoppNivaaId>
              <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
              <oppdragsId xmlns="">70450237</oppdragsId>
              <fagsystemId xmlns="">10002227</fagsystemId>
              <kid xmlns="" />
              <utbetalesTilId xmlns="">12345678901</utbetalesTilId>
              <utbetalesTilNavn xmlns="">SJOKOLADEKAKE BEMERKELSES</utbetalesTilNavn>
              <bilagsType xmlns="">U</bilagsType>
              <forfall xmlns="">2024-02-07</forfall>
              <feilkonto xmlns="">false</feilkonto>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-11-01</faktiskFom>
                <faktiskTom xmlns="">2023-11-30</faktiskTom>
                <kontoStreng xmlns="">0510000</kontoStreng>
                <behandlingskode xmlns="">0</behandlingskode>
                <belop xmlns="">-10786.00</belop>
                <trekkVedtakId xmlns="">13002940</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">0</linjeId>
                <sats xmlns="">0.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">30.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">0</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="" />
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">FSKTSKAT</klassekode>
                <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">SKAT</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-11-01</faktiskFom>
                <faktiskTom xmlns="">2023-11-30</faktiskTom>
                <kontoStreng xmlns="">4952000</kontoStreng>
                <behandlingskode xmlns="">2</behandlingskode>
                <belop xmlns="">24514.00</belop>
                <trekkVedtakId xmlns="">0</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">2</linjeId>
                <sats xmlns="">24514.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">1.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">10</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="">fcce6916-5c7b-4ad3-86b5-fac667</delytelseId>
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">SUUFORE</klassekode>
                <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">YTEL</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
            </beregningStoppnivaa>
          </beregningsPeriode>
          <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
            <periodeFom xmlns="">2023-12-01</periodeFom>
            <periodeTom xmlns="">2023-12-31</periodeTom>
            <beregningStoppnivaa>
              <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
              <stoppNivaaId xmlns="">12</stoppNivaaId>
              <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
              <oppdragsId xmlns="">70450237</oppdragsId>
              <fagsystemId xmlns="">10002227</fagsystemId>
              <kid xmlns="" />
              <utbetalesTilId xmlns="">12345678901</utbetalesTilId>
              <utbetalesTilNavn xmlns="">SJOKOLADEKAKE BEMERKELSES</utbetalesTilNavn>
              <bilagsType xmlns="">U</bilagsType>
              <forfall xmlns="">2024-02-07</forfall>
              <feilkonto xmlns="">false</feilkonto>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-12-01</faktiskFom>
                <faktiskTom xmlns="">2023-12-31</faktiskTom>
                <kontoStreng xmlns="">0510000</kontoStreng>
                <behandlingskode xmlns="">0</behandlingskode>
                <belop xmlns="">-10786.00</belop>
                <trekkVedtakId xmlns="">13002940</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">0</linjeId>
                <sats xmlns="">0.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">31.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">0</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="" />
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">FSKTSKAT</klassekode>
                <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">SKAT</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
              <beregningStoppnivaaDetaljer>
                <faktiskFom xmlns="">2023-12-01</faktiskFom>
                <faktiskTom xmlns="">2023-12-31</faktiskTom>
                <kontoStreng xmlns="">4952000</kontoStreng>
                <behandlingskode xmlns="">2</behandlingskode>
                <belop xmlns="">24514.00</belop>
                <trekkVedtakId xmlns="">0</trekkVedtakId>
                <stonadId xmlns="" />
                <korrigering xmlns="" />
                <tilbakeforing xmlns="">false</tilbakeforing>
                <linjeId xmlns="">2</linjeId>
                <sats xmlns="">24514.00</sats>
                <typeSats xmlns="">MND</typeSats>
                <antallSats xmlns="">1.00</antallSats>
                <saksbehId xmlns="">SU</saksbehId>
                <uforeGrad xmlns="">10</uforeGrad>
                <kravhaverId xmlns="" />
                <delytelseId xmlns="">fcce6916-5c7b-4ad3-86b5-fac667</delytelseId>
                <bostedsenhet xmlns="">8020</bostedsenhet>
                <skykldnerId xmlns="" />
                <klassekode xmlns="">SUUFORE</klassekode>
                <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                <typeKlasse xmlns="">YTEL</typeKlasse>
                <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                <refunderesOrgNr xmlns="" />
              </beregningStoppnivaaDetaljer>
            </beregningStoppnivaa>
          </beregningsPeriode>
        </simulering>
      </response>
    </simulerBeregningResponse>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
    """.trimIndent()
}
