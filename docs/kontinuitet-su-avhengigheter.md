# Kontinuitet og systemavhengigheter i SU

**Status:** Foreløpig arbeidsdokument. Ikke godkjent domenedokumentasjon og ikke fastsatte kontinuitetskrav.

Dokumentet avgrenser vurderingen til SU-systemet og hvordan andre systemer kan påvirke innsending og registrering av søknader og behandling av saker. Backendens kallrekkefølge og feilhåndtering er undersøkt i kode 11. november 2026. Dette er ikke en nedetidstest eller en kontroll av hva som er satt i produksjon. Frontend, eksterne systemers interne oppførsel og driftsavtaler er ikke verifisert her.

Alder og uføre vurderes samlet per flyt, etter avtalt avgrensning. Eventuelle forskjeller i nødvendige oppslag, frister eller konsekvenser beskrives som unntak der de påvirker vurderingen.

## Statusmerking

- **DOKUMENTERT** betyr at en lokal domenekontekst beskriver flyten eller nevner systemgrensen. Det bekrefter ikke oppetidskrav eller oppførsel ved nedetid.
- **KODEVERIFISERT** betyr at kallrekkefølge eller feilhåndtering er kontrollert i de oppgitte kildefilene. Det bekrefter ikke produksjonsstatus, fullført levering hos mottaker eller akseptabel avbruddstid.
- **ANTAKELSE** betyr at dette er en mulig avhengighet eller virkning som må undersøkes.
- **VURDERING** betyr at punktet må avgjøres av fag, systemeier eller drift. Det er ikke et vedtatt krav.
- **OPPLYST AV TEAMET** betyr en opplysning gitt av systemteamet i arbeidet med dette dokumentet. Den er ikke selvstendig kodeverifisert her, men behandles som teamets opplysning.

Ingen kritikalitet eller tidskrav er fastsatt her: verken mål for gjenopprettingstid (RTO), maksimalt akseptabelt datatap (RPO) eller lengste tolererbare avbrudd (MTPD). Ikke bruk utkastet alene som grunnlag for beredskap eller prioritering.

## Kandidatflyter og steg

ID-ene brukes i vurderingstabellen. Brevgenerering, journalføring og distribusjon inngår i flyten som utløser brevet. De er ikke egne hovedprosesser her. Fritekstbrev står som en egen kandidatflyt.

| Prosess-/steg-ID | Flyt og steg | Status og hva som må undersøkes |
|---|---|---|
| INN-01 | Innsending og lagring av søknad | **KODEVERIFISERT:** tilgangs- og personoppslag kreves før søknaden lagres. Frontendens innsending og kvittering er ikke undersøkt. K1, K2. |
| INN-02 | Journalføre søknad, opprette oppgave og søknadsbehandling | **KODEVERIFISERT:** søknaden er lagret før PDF, førsteside, journalføring og oppgave forsøkes. Søknadsbehandling opprettes etter journalføring og oppgave. Mangler håndteres også av reparasjonsjobb. K2. |
| SØK-01 | Opprette søknadsbehandling og hente eller registrere grunnlag | **KODEVERIFISERT:** videre arbeid har tilgangskontroll gjennom personoppslag. Skattegrunnlag hentes i en egen operasjon og lagres. K1, K3, K10. |
| SØK-02 | Vilkår, grunnlag, beregning og eventuell simulering | **KODEVERIFISERT:** beregning bruker saksdata og satser; simulering kaller SU-proxy og må lykkes før simulert tilstand lagres. Tilgangskontrollen gjelder også beregningen. K1, K3, K4. |
| SØK-03 | Attestering, vedtak og utbetaling | **KODEVERIFISERT:** innvilgelse kontrollsimuleres. Når brev skal genereres ved iverksettelse, kreves persondata, nødvendige navn og PDF før ferdigstilling. Utbetaling sendes som del av iverksettelsen. Journalføring og distribusjon av vedtaksbrevet skjer senere. K5–K8. |
| REV-01 | Opprette revurdering og hente eller oppdatere grunnlag | **KODEVERIFISERT:** opprettelse av ordinær revurdering krever at Oppgave svarer med oppgave-ID. K9. |
| REV-02 | Vurdere, beregne og simulere revurdering | **KODEVERIFISERT:** beregning/simulering bruker lagrede saksdata; simuleringskallet må lykkes. Personoppslag kreves i tilgangskontrollen og ved enkelte grunnlagsendringer. K1, K4, K9. |
| REV-03 | Attestere, iverksette og sende eventuell utbetalingsendring | **KODEVERIFISERT:** kontrollsimulering og eventuell brevgenerering skjer før transaksjonen ferdigstilles. K9. |
| ST-01 | Stanse ytelse | **KODEVERIFISERT:** simulering ved opprettelse/oppdatering, kontrollsimulering ved iverksettelse og sending til Oppdrag. Ingen brevgenerering i disse servicestegene. K11. |
| ST-02 | Gjenoppta ytelse | **KODEVERIFISERT:** tilsvarende avhengighet av simulering, kontrollsimulering og sending til Oppdrag. Ingen brevgenerering i disse servicestegene. K11. |
| KLA-01 | Registrere og behandle klage | **KODEVERIFISERT:** opprettelse krever Oppgave og normalt kontroll av journalpost i SAF. Kontrollen av journalposttilknytning hoppes over for den eksplisitte Infotrygd-varianten. K12. |
| KLA-02 | Attestere og oversende klage til klageinstansen | **KODEVERIFISERT:** person/adresse, oversendelsesbrev, journalpostreferanse til opprinnelig vedtak for ordinær sak og vellykket Kabal-kall kreves. K12. |
| REG-01 | Regulering, inkludert eksterne beløp og utbetalingspublisering | **KODEVERIFISERT:** eksterne beløp hentes fra arbeidsavklaringspenger og Pesys for aktuelle fradrag. Feilet sending etter lagring har egen retry-flyt med begrenset kjørevindu. K13. |
| TIL-01 | Vurdere og iverksette tilbakekreving | **KODEVERIFISERT:** person/adresse eller dødsbo må avklares, og SU-proxy må returnere vellykket svar på tilbakekrevingsvedtak før iverksettelsen lagres. K14. |
| TIL-02 | Generere, journalføre og distribuere tilbakekrevingsbrev | **KODEVERIFISERT:** vedtaksbrevet genereres av hendelseskonsument etter iverksettelsen. Dette er forskjellig fra ordinær søknadsbehandling. K14. |
| BREV-01 | Fritekstbrev, journalføring og distribusjon | **KODEVERIFISERT:** brevet genereres og lagres før journalføring og distribusjon i jobber. Eventuell førsteside må genereres før lagring. K6, K8, K15. |

## Hva systemavhengighetene faktisk blokkerer

Tabellen er **KODEVERIFISERT** innenfor de oppgitte flytene. Den beskriver teknisk blokkering, ikke virksomhetens kritikalitetsnivå. «Kan fortsette» forutsetter at øvrige nødvendige systemer virker. Håndterte feilreturer er ikke en garanti for at alle nettverksbrudd, tidsavbrudd eller uventede exceptions får samme resultat.

| System eller integrasjon | Berørte steg | Hva stopper? | Hva kan fortsette, og hvordan gjenopptas arbeidet? | Belegg |
|---|---|---|---|---|
| SU sin PostgreSQL-database | Alle kartlagte flyter | Ordinær lesing og lagring av sak, søknad, behandling, vedtak og dokument krever databasen. | Ingen databaseuavhengig saksbehandlingsflyt er påvist i disse kallkjedene. Backup og gjenopprettingsevne må avklares utenfor denne kodekartleggingen. | K2, K3, K5, K8, K14 |
| Microsoft Entra ID, tidligere Azure AD | Autentiserte operasjoner og integrasjoner som henter token | Manglende nødvendige token eller signeringsnøkler hindrer kallene som trenger dem. | Backendens signaturkontroll cacher signeringsnøkler; Entra-bortfall er derfor ikke det samme som at alle eksisterende brukerøkter stopper straks. Tokenutveksling er en egen avhengighet. Frontendens innlogging er ikke undersøkt. | K1, K16 |
| Persondata fra PDL | INN-01 og person-/sakstilgang i manuelle behandlingssteg; brevgenerering og adressekontroll | Tilgangssjekk og nødvendige personoppslag stopper når data ikke kan hentes. Lagrede saksdata er ikke nok til å omgå tilgangssjekken. | Persondata kan treffes i en lokal cache med 30 minutters levetid. Brukercachens nøkkel inneholder fødselsnummer og token. Den er ingen generell reserveløsning for nye personer, token eller omstart. | K1, K6 |
| Kodeverk | Navn på poststed og kommune i personoppslag | Feilretur fra disse oppslagene gir manglende navn i personmodellen, ikke avvist personoppslag. | Personmodellen kan returneres med kode/postnummer og uten beskrivende navn. Dette gjelder disse kodeverksoppslagene, ikke alle mulige klientfeil. | K1 |
| Kontakt- og reservasjonsregisteret (KRR) | Utvidet personvisning | Håndtert oppslagsfeil gir ingen kontaktinformasjon i svaret. | Backend returnerer persondata med `kontaktinfo = null`. Frontendens eventuelle krav til feltene er ikke undersøkt. | K1 |
| Skjermingsregisteret | Utvidet personvisning | Separat skjermingsoppslag kaster ved HTTP-feil og kan stoppe hele dette personoppslaget. | Dette separate kallet brukes ikke av alle backendoperasjoner. Ordinær person-/sakstilgang kontrolleres også via PDL. Frontendens avhengighet av den utvidede personvisningen må avklares. | K1 |
| SU sin PDF-generator | INN-02, SØK-03, REV-03, KLA-02, TIL-02, BREV-01 | Søknads-PDF og brevgenerering stopper. Når vedtaksbrev skal genereres under ordinær iverksettelse, stopper også iverksettelsen. | Ny søknad er allerede lagret og kan få PDF/journalpost senere. Allerede lagrede brev kan journalføres/distribueres uten å genereres på nytt. Tilbakekrevingsbrev genereres etter iverksettelsen, så dette bortfallet stopper brevleveransen der, ikke den allerede lagrede iverksettelsen. | K2, K5–K8, K14 |
| Førstesidegenerator | INN-02 og brev med valgt førstesidevedlegg | Sammensetting av søknads-PDF med førsteside og brev med slikt vedlegg stopper. | Søknaden er allerede lagret. Reparasjonsjobben forsøker journalføringsløpet på nytt. Fritekstbrev uten førsteside bruker ikke dette tilleggssteget. | K2, K6, K15 |
| Dokarkiv/Joark, skriving | INN-02 og brevsteg i øvrige flyter | Søknaden kommer ikke videre til oppgave/behandling uten journalpost. Journalføring av ferdige brev og dermed videre distribusjon stopper. | Søknaden eller brevet kan allerede være lagret i SU. Jobber forsøker journalføring på nytt. Ordinært søknadsvedtak kan ferdigstilles før vedtaksbrevet journalføres. Klageoversending for ordinær sak krever derimot at opprinnelig vedtak har journalpostreferanse. | K2, K8, K12 |
| Saksarkivets lesetjeneste SAF | KLA-01 og visning av journalposter | Opprettelse av ordinær klage stopper hvis journalpostens sakstilknytning ikke kan bekreftes. Journalpostlisten kan heller ikke hentes. | Infotrygd-varianten av klageopprettelse hopper over akkurat tilknytningskontrollen. Ikke likestill SAF-lesing med Dokarkiv-skriving eller anta at alle behandlinger gjør dette SAF-kallet. | K12, K15 |
| Oppgave, brukt blant annet gjennom Gosys | INN-02, REV-01, KLA-01 og oppgaveendringer i behandling | Opprettelse av ordinær revurdering og klage stopper ved feil i oppgaveopprettelsen. Søknad får ikke oppgave og automatisk søknadsbehandling før oppgaveopprettelsen lykkes. | Ny søknad er likevel lagret, med retry av manglende oppgave. I undersøkte steg for å sende eksisterende søknadsbehandling/klage til attestering logges håndterte oppdateringsfeil uten å avvise behandlingsovergangen. Det er ikke påvist samme automatiske reparasjon for alle oppgaveoppdateringer. | K2, K3, K9, K12 |
| Simulering i Oppdrag gjennom SU-proxy | SØK-02–SØK-03, REV-02–REV-03, ST-01–ST-02, REG-01 | Simulering og kontrollsimulering krever svar i samme operasjon. Feil hindrer den aktuelle overgangen. Stans/gjenopptak er derfor også avhengige av simulering, ikke bare meldingssending. | Registrerte søknader og lagrede grunnlag består. Beregning og andre steg som ikke selv trenger simulering, kan utføres hvis øvrige avhengigheter virker. Kontrollsimulering må fortsatt lykkes ved aktuell iverksettelse selv om en eldre simulering finnes. | K3–K5, K9, K11, K13 |
| Meldingskøen IBM MQ mot Oppdrag | Sending og kvittering for utbetaling, stans, gjenopptak og regulering | Sendefeil hindrer ordinær iverksettelse som sender utbetaling. Manglende eller feil kvittering kan blokkere senere utbetalingsoperasjoner på saken. | Ved håndtert sendefeil kaster ordinære iverksettelsesflyter slik at databasetransaksjonen avbrytes. Regulering har en annen strategi: lagring før sending og markering for retry. En vellykket køsending er ikke bekreftet utbetaling til mottaker. | K5, K7, K9, K11, K13 |
| Kabal | KLA-02 | Klageoversending stopper ved feilretur. Kallet skjer inne i transaksjonen; håndtert feil utløser rollback av lagringen der. | Tidligere registrering og vurdering av klagen består. Ingen bakgrunnskø for akkurat denne oversendingen er påvist i metoden; nytt forsøk må håndteres gjennom flyten. | K12 |
| Microsoft Graph, navn på Nav-ansatte | Brev som bruker saksbehandler-/attestantnavn | Henting av nødvendige navn feiler, og dokumentgenereringen returnerer feil. Dermed kan også aktuell iverksettelse eller klageoversending stoppe. | Ferdig genererte og lagrede dokumenter trenger ikke navnene hentet på nytt for journalføring/distribusjon. Enkelte maler bruker ikke slike navn; systemident og manglende ident har egne grener. | K6 |
| Dokumentdistribusjon, Dokdist | Brevsteg i alle aktuelle flyter | Bestilling av utsending stopper. Journalføring må være gjennomført først. | Dokument og journalpost kan allerede være lagret. Distribusjonsfeil registreres, og jobb forsøker på nytt med ventetid mellom forsøk. Dette ruller ikke tilbake et allerede lagret vedtak. | K8 |
| Skatteopplysninger gjennom Sigrun | Innhenting/oppdatering av skattegrunnlag i SØK-01 | Ny innhenting av skatteopplysninger kan ikke gi ordinært oppslagsresultat ved bortfall. Skatteoppslagets feil behandles som del av skattegrunnlagsmodellen. | Innhenting er en egen operasjon; tidligere hentet grunnlag ligger på behandlingen. Det er ikke et nytt direkte Sigrun-kall i den undersøkte iverksettelsesmetoden. Om lagrede opplysninger er faglig tilstrekkelige, må vurderes separat. | K10 |
| Arbeidsavklaringspenger, AAP-integrasjonen | REG-01 med relevant fradrag | Oppslag for aktuelle personer feiler; harde oppslagsfeil gir feilresultat for den aktuelle saken. | Personer uten dette fradraget krever ikke dette oppslaget i den undersøkte reguleringskoden. Dette dokumenterer ikke en generell avhengighet i all søknadsbehandling. | K13 |
| Pensjonssystemet Pesys | REG-01 med relevante pensjonsdata | Nødvendige regulerings-/omregningsbeløp kan ikke utledes ved oppslagsfeil. | Virkningen avhenger av hvilke fradrag og personer kjøringen faktisk skal hente. Oppslagsfeil skilles fra vellykkede resultater per berørt person/sak. | K13 |
| Tilbakekreving i Oppdrag gjennom SU-proxy | TIL-01 | Synkront kall for tilbakekrevingsvedtak må lykkes før iverksettelseshendelsen lagres. Dette er ikke samme sendemåte som utbetalingsoppdrag over IBM MQ. | Tidligere lagret vurdering består. Koden beskriver et uavklart tilfelle der ekstern sending lykkes, men lokal lagring etterpå feiler; trygg gjentakelse må ikke tas for gitt. | K14 |
| Antivirus, ClamAV | Opplasting av PDF som brev på sak | Skanning feiler før dokumentet lagres; opplastingen stopper. | Denne skanningen er ikke en avhengighet for vanlig malbasert brevgenerering. Eventuell førsteside til opplastet brev er en separat avhengighet. | K15 |
| Regoppslag, mottakeradresse | Oppslag av alternativ mottakeradresse | Det konkrete adresseoppslaget stopper ved feil. | Dette er en egen oppslagsoperasjon, ikke et påvist obligatorisk kall i alle ordinære søknadsbehandlinger. | K15 |

### PDF-generator og PDF-bygger må ikke blandes sammen

`PdfByggerService` velger leverandør per dokumentmal og miljø. Alle malene i den undersøkte `DokumentMal.kt` har `pdfByggerProd = false`, også søknad, klage og fritekstbrev. Gjeldende kode velger dermed den gamle PDF-generatoren for disse malene i produksjonsmodus. At PDF-bygger-klienten er konfigurert, er ikke alene belegg for at den er en aktiv produksjonsavhengighet. Ingen automatisk overgang til en annen generator ved nedetid er påvist i denne rutingen. K17.

### Retry er ikke det samme som et gjenopprettingstidskrav

Reparasjonsjobben for søknader (FiksSøknader) kjører hver time mandag–fredag 06–21 (Oslo), velger søknader eldre enn fem minutter og oppretter manglende journalpost, deretter oppgave, deretter søknadsbehandling. Behandling opprettes bare når søknaden er journalført, har oppgave og ikke er lukket; jobben endrer ikke eksisterende oppgaver generelt. Dette betyr ikke at søknader alltid ferdigstilles innen én time, særlig ikke utenfor kjørevinduet. K2; `service/.../søknad/job/FiksSøknader.kt:24–41`, `SøknadServiceImpl.kt:170–250`, `StartJobberOgConsumers.kt:605–611`, `common/.../job/RunJobCheck.kt:47–59`.

For brev finnes flere jobber med ulik takt. Klassisk journalføring og distribusjon (JournalførDokumentJob/DistribuerDokumentJob) kjører hvert femte minutt uten kalendervindu, normalt maks ti dokumenter per kjøring, eldste først, og distribusjonen har eksponentiell backoff per brev opp til ett døgn. Hendelsesbaserte dokumentjobber kjører hvert minutt uten kalendervindu, og distribusjonsfeil i den hendelsesbaserte flyten forsøkes neste kjøring uten backoff. Alle krever at dokumentet allerede er generert, lagret og journalført før distribusjon, og en bestilt distribusjon er ikke bekreftet levering. `StartJobberOgConsumers.kt:415–431,572–578`; `JournalførDokumentService.kt:33–81`; `DistribuerDokumentService.kt:43–81`; `dokument/.../DokumentPostgresRepo.kt:310–338`; `common/.../backoff/ExponentialBackoff.kt:12–34`; `DokumentJobber.kt:22–31`.

Tilbakekrevingsjobbene kjører hvert minutt og genererer vedtaksbrev og egne oppgaver (forhåndsvarsel, attestering, underkjennelse, lukking); feil markeres som ikke konsumert og kan forsøkes igjen. Dette dekker ikke ordinær søknad eller klage. `StartJobberOgConsumers.kt:579–589`; `Tilbakekrevingsjobber.kt:38–51`; `OppdaterOppgaveForTilbakekrevingshendelserKonsument.kt:60–110,191–217`; `GenererVedtaksbrevTilbakekrevingKonsument.kt:59–138`.

Reguleringsretryen for ikke-oversendte utbetalinger har kjørebetingelse bare i juni og henter iverksatte reguleringer for inneværende år der utbetaling ikke er sendt til Oppdrag. Ikke legg til grunn at denne jobben automatisk rydder opp i november eller i eldre års reguleringer; annen automatisk dekning utenfor juni er ikke påvist. K13; `ReguleringServiceImpl.kt:220–310`, `RetryIverksettReguleringJob.kt:35–53`, `database/.../regulering/ReguleringPostgresRepo.kt:350–364`.

Oppgaveoppdateringer som bare logges ved feil i attesteringsoverganger, må ikke beskrives som automatisk reparert uten egen kode- eller driftsdekning; ingen automatisk reparasjon er påvist akkurat der (`SøknadsbehandlingServiceImpl.kt:286–327`, `KlageServiceImpl.kt:274–294`). Rollback av en lokal transaksjon kan heller ikke trekke tilbake et eksternt kall som allerede har lyktes; et uklart sendingsresultat krever avstemming før gjentakelse. Hvor lenge eksterne køer og tjenester beholder meldinger, er en avhengighet av de aktuelle systemene og analyseres ikke her.

### Særskilt om Oppdrag

At SU er utilgjengelig, betyr ikke automatisk at Oppdrag stanser arbeidet med oppdrag som allerede er mottatt. Undersøk mottaksbekreftelse, kø, videre behandling, kvitteringer og ansvar for oppfølging på hver side av grensen. Behandle dette som et eget kontinuitetsspørsmål, adskilt fra SU sin evne til å lage og sende nye oppdrag.

**OPPLYST AV TEAMET (denne samtalen):** oppdrag som allerede er mottatt hos Oppdrag mens SU er nede, blir iverksatt og utbetalt. Dette gjelder presist allerede mottatte oppdrag, ikke nye endringer, nye stansoppdrag eller hele framtidige stønadsperioder som ennå ikke er sendt. Det betyr heller ikke at alle løpende ytelser fortsetter ubegrenset; nye vedtak og utbetalinger som SU ikke rekker å sende, er ikke dekket.

### Hvilke operasjoner trenger eksternt svar, og hva bruker lagrede data

Dette skiller operasjoner som må ha svar fra et eksternt system i samme operasjon, fra operasjoner som bruker allerede lagrede data. Skillet gjelder tre tilstander: SU helt nede (ingenting kjører), ett nabosystem nede (bare operasjoner som treffer akkurat det systemet blokkeres) og SU oppe igjen (lagrede data består, mens utestående eksterne kall må gjentas eller avstemmes).

- **Må ha eksternt svar i operasjonen:** tilgangskontroll og personoppslag (PDL), simulering og kontrollsimulering (SU-proxy), utbetalingssending og kvittering (IBM MQ), oppgaveopprettelse ved ordinær revurdering og klage (Oppgave), journalføring (Dokarkiv), klageoversending (Kabal), journalpost-/sakstilknytningskontroll ved ordinær klage (SAF), tilbakekrevingsvedtak (SU-proxy), PDF-generering, saksbehandler-/attestantnavn ved aktuelle maler (Graph), eksterne beløp i regulering (AAP, Pesys) og ny innhenting av skattegrunnlag (Sigrun).
- **Bruker lagrede data:** beregning (lokal), lagrede vilkår/grunnlag/skattegrunnlag, lagret søknad og søknadsinnhold, og allerede genererte og lagrede brev for senere journalføring og distribusjon.
- **Ingen eksplisitt ferskhets- eller gyldighetsgrense** for lagrede grunnlagsdata er funnet i den undersøkte koden. Om lagrede opplysninger fortsatt er faglig tilstrekkelige, er en fagvurdering, ikke et dokumentert systemkrav. Databasebevaring ved ordinær nedetid (systemet oppe igjen uten datatap) forutsetter ikke datatap og skal ikke blandes med gjenoppretting (restore) etter datatap.

## Grunnlag for prioritering

Ikke likestill antall saker eller produksjonsvolum med alvorlighetsgrad. Vurder separat:

- hvor mange søknader eller behandlinger som blir forsinket
- arbeidstid og årsverk som trengs for å ta igjen et etterslep
- kalenderbasert tid fra mottak til avgjørelse, inkludert venting på systemer eller eksterne svar
- rettslige frister, utbetalingsdatoer og andre tidsavhengige konsekvenser
- konsekvenser for personer som er avhengige av stønaden, særlig sårbare brukere
- om forsinkelsen kan rettes opp, og om den kan føre til tap av rettighet, feil utbetaling eller manglende informasjon

Intern statistikk og saksstatistikk kan underbygge volum, daglig produksjon, restanse og behandlingstid. Statistikkleveranser er ikke en kritisk prosess i denne vurderingen. Bruk ikke produksjonsmål alene som mål på kalenderbasert behandlingstid eller konsekvens for den enkelte.

## Lovbestemte frister og rettslige holdepunkter

Dette skiller fire ting som ikke må settes lik hverandre: **lovkrav**, **lokale operasjonelle utbetalingsdatoer/frister**, **dokumentert regel i kode** og **VURDERING**. Lovkravene under er verifisert mot Lovdata 11. november 2026. De angir ikke et RTO, RPO eller MTPD, og loven setter ikke et universelt 24-timers krav.

**Rettsgrunnlag per 11. november 2026**

- Lov 29. april 2005 nr. 21 om supplerande stønad til personar med kort butid i Noreg gjelder og er sist endret ved lov 7. mai 2026 nr. 15. <https://lovdata.no/dokument/NL/lov/2005-04-29-21>
- Forvaltningsloven av 10. februar 1967 gjelder fortsatt. Ny forvaltningslov (lov 20. juni 2025 nr. 81) er vedtatt, men trer i kraft «fra den tiden Kongen bestemmer»; Lovdata-kilden (oppdatert per august 2025) oppgir at den ennå ikke var satt i kraft. Ikrafttredelse etter dette er ikke bekreftet i kildene som er sjekket her, så vurderingen bygger på 1967-loven. <https://lovdata.no/dokument/NL/lov/1967-02-10> og <https://lovdata.no/dokument/NL/lov/2025-06-20-81>

**Konkrete bestemmelser (KILDE, lov):**

| Tema | Bestemmelse | Innhold (verifisert) |
|---|---|---|
| Saksbehandlingstid | forvaltningsloven § 11 a | Saken skal forberedes og avgjøres «uten ugrunnet opphold». Foreløpig svar skal gis i sak om enkeltvedtak dersom svar ikke kan gis innen en måned. Ingen fast timefrist. |
| Forvaltningslov gjelder | su-loven § 15 | Forvaltningslovas regler gjelder for saker om supplerende stønad, med unntakene i su-loven. |
| Klage | forvaltningsloven kap. VI, § 28–29 | Alminnelig klagefrist er tre uker fra underretning om vedtaket. Eksakt ordlyd i § 29 ble ikke lest ordrett her; treukersfristen er bekreftet gjennom § 28 (kap. VI om klage) og henvisningen i fvl. § 24. |
| Anke | su-loven § 22 | Vedtak kan ankes til Trygderetten; folketrygdloven § 21-12 gjeld tilsvarande. |
| Utbetaling | su-loven § 11 siste ledd | «Stønaden vert utbetalt i månadsbeløp før utgangen av den einskilde månaden.» Utbetalingsfristen er månedlig (før månedsslutt), ikke et døgnkrav. |
| Virkningstidspunkt | su-loven § 11 | Stønad gis fra og med måneden etter at vilkårene var oppfylt; ved nedsettelse fra måneden etter endringen; ikke lenger tilbake enn tre måneder før søknadsmåneden. |
| Brukers meldeplikt | su-loven § 18 | Søker har opplysningsplikt, og den som mottar stønad skal gi melding om endringar som kan ha betydning for stønaden. Her står det ikke «utan ugrunna opphald». Dette er brukers plikt, ikke forvaltningens saksbehandlingstid. |
| Tredjeparts opplysningsplikt | su-loven § 19 fjerde ledd | Tredjeparter som pålegges å gi opplysninger til etaten (bl.a. Skatteetaten, sosialtenesta, politiet, offentlege organ, arbeidsgivar, bankar, forsikringsselskap, pensjonsordningar, helseinstitusjonar, fengsel), «har plikt til å gjere dette utan ugrunna opphald og utan hinder av teieplikt». Fristen gjelder tredjeparts levering til etaten – ikke brukers meldeplikt og ikke etatens egen saksbehandlings- eller gjenopprettingstid. |
| Tilbakekreving | su-loven § 13 | Hjemmel for tilbakekreving; avregning i framtidige terminer maks 10 % av terminbeløpet. |

**VURDERING:** Den rettslig styrende normen for saksbehandling er «uten ugrunnet opphold» (forvaltningsloven § 11 a), ikke en fast frist i timer. Den konkrete tidskritiske datoen for en løpende sak er den månedlige utbetalingen før månedsslutt (su-loven § 11). Et avbrudd nær månedsskiftet kan derfor ha en helt annen konsekvens enn samme avbrudd tidlig i måneden. Lokale og operasjonelle utbetalingsdatoer og interne frister i Nav/Oppdrag er ikke verifisert her og må hentes fra drift. Loven skal ikke oversettes direkte til RTO/RPO/MTPD; den er ett av flere grunnlag for å fastsette disse.

## Foreløpig volumgrunnlag

Kilde er innlimte, aggregerte resultater fra brukerens kjøring av PostgreSQL-spørringer for 1. august til 31. oktober 2026. Agenten har ikke kjørt spørringene eller kontrollert produksjonsdatabasen. Perioden har 65 mandag–fredag-dager; dette er ikke en bemanningskalender.

Det ble registrert 1 868 søknader: 1 818 digitale og 50 papirsøknader. Av disse ble 1 864 registrert mandag–fredag, tilsvarende 28,68 per slik dag. Registreringstidspunktet i SU er ikke nødvendigvis faktisk mottaksdato for papirsøknader. Mislykkede innsendingsforsøk og søknader som ennå ikke er registrert, inngår ikke.

Behandlingsuttrekket teller hver behandling ved første registrerte utfall, ikke alle arbeidsoperasjoner eller senere utfall etter retur. Alder og uføre er summert:

| Flyt | Iverksatt | Oversendt til klageinstansen | Avsluttet eller avbrutt | Totalt |
|---|---:|---:|---:|---:|
| Søknadsbehandling | 1 861 | 0 | 73 | 1 934 |
| Revurdering | 641 | 0 | 110 | 751 |
| Gjenopptak | 369 | 0 | 2 | 371 |
| Stans | 457 | 0 | 2 | 459 |
| Klage | 31 | 26 | 14 | 71 |
| Regulering av grunnbeløp | 22 | 0 | 3 | 25 |
| Tilbakekreving | 2 | 0 | 4 | 6 |
| **Sum** | **3 383** | **26** | **208** | **3 617** |

Stansene i dette uttrekket er merket automatiske. De øvrige radene er merket manuelle. Dette beskriver statistikkens behandlingsmetode, ikke målt arbeidsinnsats. Perioden dokumenterer ikke volumet ved den ordinære årlige reguleringen.

Omtrent 26,2 søknadsbehandlinger ble iverksatt per mandag–fredag-dag, beregnet ved å summere de avrundede gjennomsnittene for alder og uføre. Ikke divider hele periodens antall på 65 for å gjenskape dette målet: totalantallet inkluderer også helgeaktivitet.

**VURDERING:** Tallene underbygger omfanget av mulig utsatt produksjon, men fastsetter ikke kritikalitet eller akseptabel avbruddstid. Antall registrerte søknader og antall søknadsvedtak gjelder ikke nødvendigvis samme søknader, og differansen er ikke et mål på restanseendring.

Statistikkens `mottatt_tid` settes i dagens mapper til behandlingens opprettelsestid. Medianene fra uttrekket beskriver derfor forløp fra dette tidspunktet til første utfall, ikke nødvendigvis fra faktisk søknadsmottak. De måler ikke aktiv arbeidstid. Gruppevise medianer kan ikke summeres eller gjennomsnittsberegnes til en felles median.

Gjennomsnittsvolum brukes som grunnlag for omfang. Aktivitetstopper og aldersfordeling inngår ikke i den avtalte vurderingen. Kodekartleggingen over viser hvilke steg integrasjonene blokkerer. For å vurdere konsekvenser og tidskrav trengs særlig relevante frister og avbruddstidspunkt. Bemanning, faktisk arbeidstid og tilgjengelig ekstrakapasitet trengs dersom innhenting av etterslep skal tallfestes.

### Åpne behandlinger i statistikken

Kilde er brukerens innlimte resultat fra restansespørringen, mottatt 11. november 2026. Tallene er summert på tvers av alder og uføre og viser behandlinger med siste statistikkstatus utenfor `IVERKSATT`, `AVSLUTTET`, `AVBRUTT` og `OVERSENDT`.

| Flyt | Åpne behandlinger |
|---|---:|
| Søknadsbehandling | 662 |
| Revurdering | 277 |
| Tilbakekreving | 212 |
| Klage | 37 |
| Regulering av grunnbeløp | 19 |
| Gjenopptak | 1 |
| **Sum** | **1 208** |

Av disse er 164 registrert til attestering: 130 søknadsbehandlinger, 32 revurderinger og 2 tilbakekrevingsbehandlinger. Uttrekket inneholder ingen åpne stansbehandlinger.

Dette er statistikkens registrerte beholdning, ikke en kontroll mot de operative behandlingstabellene. Det er ikke avklart om alle radene fortsatt representerer reelt utestående arbeid. Søknader uten opprettet behandling og klager som allerede er oversendt til klageinstansen, inngår ikke i denne beholdningen. Alderskolonnene fra uttrekket brukes ikke videre.

## Historisk underlag (ekstern kilde)

En verdivurdering datert 7. mai 2025, eid av Team Supplerende Stønad, ligger i sitt godkjente, skjermede lagringssted og er kilde for tidligere konsekvens-, kritikalitets- og toleransevurderinger. Innholdet er unntatt offentlighet og gjengis ikke her. Når den brukes, må den vurderes mot dagens teknikk; den fastsetter ikke dagens krav, og ingen av dens verdier er gjengitt eller valgt som løsning i dette underlaget. Faktiske systemavhengigheter er uansett kartlagt i tabellene over.

## Rammeverk for konsekvens og prioritet (referanse – ingen konklusjon trukket)

Dette er rammeverket som kan brukes ved manuell utfylling. Her trekkes **ingen** slutning om hvilket nivå eller hvilken prioritet som gjelder for SU, ingen tilordning til bestemte flyter eller persongrupper, og ingen RTO/RPO/MTPD. Selve vurderingen gjøres manuelt i Nav sin godkjente, skjermede arbeidsflate.

Konsekvensnivåer (lavest til høyest): **Ubetydelig, Lav, Moderat, Alvorlig, Svært alvorlig.** Nivå vurderes separat for aksene Brukere, Ansatte og Virksomheten (Nav), og over tid. Kriterietabellen under er rammeverkets definisjoner, ikke en vurdering av SU:

| Nivå | Brukere | Ansatte | Virksomheten (Nav) |
|---|---|---|---|
| Ubetydelig | Ikke berørt / ubetydelige avvik | Ingen skade/trusler, ikke merkbart, ingen overtid | Få serviceklager, få negative medieoppslag |
| Lav | Noen brukere berørt (forsinket utbetaling/oppfølging) eller mindre avvik | Noen trusler fra enkelte, tungvint på enkelte områder, overtid kun sesong | Noen serviceklager, enkelte negative oppslag, noen tungvinte metoder |
| Moderat | En gruppe brukere berørt | Tungvinte arbeidsmåter, noe trusler, frivillig overtid, svekket tillit hos flere | Flere serviceklager, flere negative oppslag med oppfølging/regionale media, noen uklare roller/grensesnitt |
| Alvorlig | Mange får forsinket vedtak/utbetaling, eller noen får ikke vedtak | Mer tid/energi, hyppige trusler/utrygghet, mye pålagt overtid | Mange serviceklager, mange tungvinte metoder, strengere rapporteringskrav/debatt |
| Svært alvorlig | Betydelig andel berørt, eller mange får ikke vedtak/utbetaling | Upålitelige systemer hindrer god og effektiv jobb, konstant pålagt overtid, stor mistillit | Svært høyt antall serviceklager, stadig negative oppslag / generell mistillit |

Forbehold ved bruk av rammeverket:

- Ingen nivåtilordning for SU gjøres her; tall og indikatorer er kriterier, ikke konklusjoner.
- Ikke lag en fast tidskurve (for eksempel «alltid Alvorlig etter en uke»); nivå over tid må begrunnes konkret med volumreferanse og nærhet til frister/utbetaling.
- Observert volum oversettes ikke automatisk til «mange» eller «betydelig andel» uten referansegrunnlag; antall ventende behandlinger er ikke lik antall berørte personer uten belegg.
- Matrisen har flere indikatorer per nivå, og ingen aggregeringsregel er gitt; et nivå skal ikke utledes fra én indikator alene. Ikke utled personvernbrudd, mediedebatt, helseskade, trusler, pålagt overtid eller død fra avbruddsvarighet alene.
- Allerede mottatte oppdrag hos Oppdrag iverksettes og utbetales (OPPLYST AV TEAMET); dette begrenser brukerpåvirkningen, men betyr ikke at alle løpende ytelser fortsetter ubegrenset.

Prioritetstidsbånd (rammeverk, innlimt av bruker):

| Tidsramme uten løsning | Prioritet |
|---|---|
| 12–24 timer | Prioritet 1 |
| 24 timer | Prioritet 2 |
| 24–48 timer | Prioritet 3 |
| Inntil 1 uke | Prioritet 4 |
| Inntil 1 måned | Prioritet 5 |

Tidsbåndene er et rammeverk for prioritering, ikke en fastsettelse av RTO eller MTPD og ikke en valgt prioritet for SU. Grensen ved 24 timer overlapper (12–24 t, 24 t og 24–48 t møtes der), så prioritet kan ikke leses mekanisk ut av tabellen. Ingen prioritet er valgt for SU her.

## Støtteunderlag for manuell utfylling i Excel

Selve risikovurderingen – konsekvensnivå, samlet kritikalitet, prioritet, RTO, RPO, MTPD og akseptabel avbruddstid – fylles ut manuelt i Nav sin godkjente, skjermede arbeidsflate. Her fastsettes ingen slike verdier. Tabellen viser hvilke faktagrunnlag og kilder i dette dokumentet som kan støtte utfyllingen.

| Excel-felt | Faktagrunnlag her | Hvor finnes belegg | Hva evalueres manuelt (skjermet) |
|---|---|---|---|
| Prosess / Beskrivelse / Steg / ID | Kandidatflyter og steg | «Kandidatflyter og steg» | — (kan overføres direkte) |
| Hva vi er avhengig av | Tekniske avhengigheter per steg | «Hva systemavhengighetene faktisk blokkerer», K1–K17 | — |
| Hva andre er avhengig av fra oss | Utbetalingsoppdrag, oversending, dataleveranser | «Særskilt om Oppdrag», avhengighetstabellen | — |
| Nødvendig leveranse / minstenivå | Hva som er lagret, automatisk etterarbeid, hvilke operasjoner som krever eksternt svar | «Hvilke operasjoner trenger eksternt svar …», «Retry er ikke …» | Nødvendige aktiviteter, systemer og bemanning for minstenivå |
| Konsekvens (brukere/ansatte/virksomhet) | Observert volum, funksjonell blokkering; rammeverkets kriterier | «Foreløpig volumgrunnlag», blokkeringstabell, «Rammeverk for konsekvens …» | Konsekvensnivå ut fra godkjent matrise + volum + forretningsfrister |
| RTO | Jobbkjørevinduer som tekniske observasjoner (ikke RTO) | «Retry er ikke …» | Faktiske gjenopprettingsmålinger og mål |
| RPO | Datagrupper og hva som er lagret | «Hvilke operasjoner …», Kildebelegg | Varig lagring og backup/restore-belegg (RPO ≠ backupintervall) |
| MTPD | Lov- og utbetalingsfrister | «Lovbestemte frister …» | Lov/utbetalingsfrister + forretningseiers vurdering |
| Prioritet/kritikalitet | Avhengigheter og blokkering; tidsbånd-rammeverk | Blokkeringstabell, «Rammeverk …» | Manuelt vedtatte krav og avhengigheter |
| Forutsetninger / Usikkerheter | Statusmerking, forbehold, åpne punkter | «Statusmerking», «Uavklarte punkter» | — |

Observert volum som støtte (ikke konsekvens): 28,68 registrerte søknader per mandag–fredag-dag og ca. 26,2 iverksatte søknadsbehandlinger per slik dag i perioden 1. august–31. oktober 2026 (se «Foreløpig volumgrunnlag» for kilde og forbehold).

**Rent regneeksempel (ikke en SU-prognose, ikke en konsekvens, ikke tilordnet flyt eller persongruppe):** multipliseres de observerte gjennomsnittene med antall tapte virkedager, gir det ca. 29/86/143 registreringer og ca. 26/79/131 søknadsiverksettelser ved henholdsvis 1/3/5 dager. Dette er bare aritmetikk på gjennomsnittstall til illustrasjon og er verken en forventet forsinkelse eller et valgt konsekvensnivå for SU.

Eksempelrader – de faktabaserte feltene kan overføres; risikofeltene fylles manuelt og er med vilje ikke satt her:

| ID | Prosess (aktivitet) | Hva vi er avhengig av | Nødvendig leveranse ved avbrudd | RTO/RPO/MTPD | Konsekvens | Prioritet |
|---|---|---|---|---|---|---|
| INN-01/02 | Innsending, lagring, journalføring og oppgave for søknad | PostgreSQL, Entra/token, PDL, PDF-generator, førstesidegenerator, Dokarkiv (skriv), Oppgave | Søknad lagres før journal/oppgave; FiksSøknader forsøker manglende journalpost, oppgave og behandling (man–fre 06–21) | Fylles manuelt | Fylles manuelt | Fylles manuelt |
| SØK-01–03 | Søknadsbehandling til vedtak og utbetaling | PostgreSQL, PDL, Sigrun (ny innhenting), SU-proxy (simulering/kontrollsimulering), PDF, Graph-navn, IBM MQ (utbetaling), Dokarkiv/Dokdist (brev) | Beregning og lagrede grunnlag består; simulering/kontrollsimulering og utbetalingssending krever eksternt svar; brev-journalføring og -distribusjon har retry | Fylles manuelt | Fylles manuelt | Fylles manuelt |
| ST-01/02 | Stans og gjenopptak av ytelse | PostgreSQL, SU-proxy (simulering/kontrollsimulering), IBM MQ (sending til Oppdrag) | Ingen brevgenerering i disse stegene; krever simulering og sending; allerede mottatte oppdrag hos Oppdrag utbetales (OPPLYST AV TEAMET) | Fylles manuelt | Fylles manuelt | Fylles manuelt |

## Uavklarte punkter

- Hvilke søknadskanaler finnes, og når regnes en søknad som mottatt og registrert?
- Visning av lagret søknad: backend lagrer søknaden og søknadsinnholdet før journalføring og oppgave, så en søknad kan være lagret selv om behandling, journalpost eller oppgave mangler – journalpost og oppgave er ikke det samme som søknadsinnholdet. En lagret søknad kan hentes fra backend: ny søknad returneres med søknad og saksnummer også ved håndterte etterstegsfeil, og `GET /saker/{sakId}` mapper alle søknadstilstander med søknadsinnhold uten å filtrere bort søknader som mangler journalpost eller oppgave. Søknadsbehandling krever derimot at søknaden er journalført og har oppgave, og søknadsutskrift (`GET /soknad/{id}/utskrift`) genererer en ny PDF (krever person, PDF-generator og førsteside), ikke en ferdig lagret PDF. Backend-tilgjengelighet og behandlingsopprettelse er altså to ulike ting. Om og hvordan frontend åpner eller bare viser en søknad mellom tilstander er ikke sjekket, men dette blokkerer ikke Excel-utfyllingen. `web/.../routes/sak/SakRoutes.kt:270–280`, `SakJson.kt:55–61`, `søknad/SøknadJson.kt:13–37`, `SøknadService.kt:116–163,353–419`, `domain/.../søknadsbehandling/opprett/OpprettNySøknadsbehandling.kt:28–59`.
- Automatiske jobber kontra feillogging: flere steg har automatisk reparasjon (FiksSøknader for søknad; journalførings- og distribusjonsjobber med nytt forsøk; hendelsesbaserte dokument- og tilbakekrevingsjobber; reguleringsretry bare i juni og for inneværende år), mens enkelte oppgaveoppdateringer i attesteringsoverganger bare logges ved feil. Ikke konkluder at alle oppdateringer trenger manuell oppfølging, og ikke lov universell retry. Se «Retry er ikke det samme som et gjenopprettingstidskrav».
- Lovbestemte frister er oppsummert i «Lovbestemte frister og rettslige holdepunkter». Lokale og operasjonelle utbetalingsdatoer og interne frister i Nav/Oppdrag må bekreftes fra drift.
- Dagens driftsforhold og klassifiseringer: verdivurderingen 7. mai 2025 er historisk (se eget avsnitt) og må bekreftes før on-prem, 24-timers toleranse eller «Moderat» brukes som dagens krav.
- Årsverk: formuleringen betyr å oversette etterslep til arbeidsmengde (arbeidstimer per sak × antall saker → årsverk). Volumtallene er tilstrekkelige for antall forsinkede saker. Målt aktiv arbeidstid per sak og tilgjengelig kapasitet trengs bare hvis man vil tallfeste ressursbelastning eller hvor raskt restansen kan tas igjen. Kalenderbaserte medianer er ikke aktiv arbeidstid.

## Kildegrunnlag og avgrensning

**DOKUMENTERT som kart, ikke full kodeverifikasjon:** `.github/domenekontekst/systemoversikt.md`, `saksgangen.md`, `behandling.md`, `utbetaling.md`, `brev.md`, `regulering.md` og `eksterne-repos.md`. De beskriver kjente flyter og nevner integrasjonsgrenser, men fastsetter ikke kontinuitetskrav eller kritikalitet.

### Kodebelegg

Referansene gjelder den undersøkte arbeidskopien. Linjenumre kan flytte seg. Kodekartleggingen omfatter kallrekkefølge og håndterte feil, ikke en test av avbrudd eller eksterne leveransegarantier.

| ID | Kilder med linjehenvisninger |
|---|---|
| K1 | `web/src/main/kotlin/no/nav/su/se/bakover/web/services/AccessCheckProxy.kt:532–540,759–789,1994–2015`; `client/src/main/kotlin/no/nav/su/se/bakover/client/person/PersonClient.kt:49–140,172–217`; `client/src/main/kotlin/no/nav/su/se/bakover/client/person/PdlClientWithCache.kt:25–70`; `client/src/main/kotlin/no/nav/su/se/bakover/client/skjerming/SkjermingClient.kt:22–43`. |
| K2 | `service/src/main/kotlin/no/nav/su/se/bakover/service/søknad/SøknadServiceImpl.kt:116–356`; `service/src/main/kotlin/no/nav/su/se/bakover/service/søknad/job/FiksSøknader.kt:20–53`; `web/src/main/kotlin/no/nav/su/se/bakover/web/StartJobberOgConsumers.kt:605–611`. Eksisterende test for bekreftelse ved PDF-feil: `service/src/test/kotlin/no/nav/su/se/bakover/service/søknad/SøknadTest.kt:93` (lest, ikke kjørt). |
| K3 | `service/src/main/kotlin/no/nav/su/se/bakover/service/søknadsbehandling/SøknadsbehandlingServiceImpl.kt:227–327`. |
| K4 | `client/src/main/kotlin/no/nav/su/se/bakover/client/oppdrag/simulering/SimuleringProxyClientGcp.kt:60–90`; `client/src/main/kotlin/no/nav/su/se/bakover/client/ProdClientsBuilder.kt:53–62`. |
| K5 | `service/src/main/kotlin/no/nav/su/se/bakover/service/søknadsbehandling/IverksettSøknadsbehandlingServiceImpl.kt:83–150`; `domain/src/main/kotlin/no/nav/su/se/bakover/domain/søknadsbehandling/iverksett/innvilg/IverksettInnvilgetSøknadsbehandling.kt:82–100`; `domain/src/main/kotlin/no/nav/su/se/bakover/domain/søknadsbehandling/iverksett/innvilg/IverksattInnvilgetSøknadsbehandlingResponse.kt:58–100`. |
| K6 | `service/src/main/kotlin/no/nav/su/se/bakover/service/brev/BrevServiceImpl.kt:43–85`; `domain/src/main/kotlin/no/nav/su/se/bakover/domain/brev/jsonRequest/MapDokumentCommandToPdfInnhold.kt:58–94`; `client/src/main/kotlin/no/nav/su/se/bakover/client/person/MicrosoftGraphApiClient.kt:49–72`. |
| K7 | `økonomi/application/src/main/kotlin/økonomi/application/utbetaling/UtbetalingServiceImpl.kt:90–147`; `client/src/main/kotlin/no/nav/su/se/bakover/client/oppdrag/utbetaling/UtbetalingMqPublisher.kt:15–26`; `økonomi/domain/src/main/kotlin/økonomi/domain/utbetaling/Utbetalinger.kt:49–83`; `økonomi/domain/src/main/kotlin/økonomi/domain/simulering/SimuleringClient.kt:27–34`. |
| K8 | `service/src/main/kotlin/no/nav/su/se/bakover/service/dokument/JournalførDokumentService.kt:32–85`; `service/src/main/kotlin/no/nav/su/se/bakover/service/dokument/DistribuerDokumentService.kt:45–89`; `web/src/main/kotlin/no/nav/su/se/bakover/web/services/dokument/JournalførDokumentJob.kt:24–36`; `web/src/main/kotlin/no/nav/su/se/bakover/web/services/dokument/DistribuerDokumentJob.kt:22–35`. |
| K9 | `service/src/main/kotlin/no/nav/su/se/bakover/service/revurdering/RevurderingServiceImpl.kt:169–210,662–715,1032–1100`; `domain/src/main/kotlin/no/nav/su/se/bakover/domain/revurdering/iverksett/innvilg/IverksettInnvilgetRevurdering.kt:50–96`; `domain/src/main/kotlin/no/nav/su/se/bakover/domain/revurdering/iverksett/innvilg/IverksettInnvilgetRevurderingResponse.kt:45–100`. |
| K10 | `service/src/main/kotlin/no/nav/su/se/bakover/service/søknadsbehandling/SøknadsbehandlingServiceImpl.kt:941–969`; `service/src/main/kotlin/no/nav/su/se/bakover/service/skatt/SkatteServiceImpl.kt:49–76`; `vilkår/skatt/infrastructure/src/main/kotlin/vilkår/skatt/infrastructure/client/SkatteClient.kt:75–151`. |
| K11 | `service/src/main/kotlin/no/nav/su/se/bakover/service/revurdering/StansYtelseServiceImpl.kt:90–154,262–300`; `service/src/main/kotlin/no/nav/su/se/bakover/service/revurdering/GjenopptaYtelseServiceImpl.kt:100–156,231–272`. |
| K12 | `service/src/main/kotlin/no/nav/su/se/bakover/service/klage/KlageServiceImpl.kt:103–159,274–294,343–447`; `client/src/main/kotlin/no/nav/su/se/bakover/client/ProdClientsBuilder.kt:98–109`. |
| K13 | `service/src/main/kotlin/no/nav/su/se/bakover/service/regulering/ReguleringServiceImpl.kt:220–310`; `service/src/main/kotlin/no/nav/su/se/bakover/service/regulering/AapReguleringerService.kt:34–125`; `service/src/main/kotlin/no/nav/su/se/bakover/service/regulering/ReguleringerFraPesysServiceImpl.kt:67–120`; `web/src/main/kotlin/no/nav/su/se/bakover/web/services/regulering/RetryIverksettReguleringJob.kt:35–58`. |
| K14 | `tilbakekreving/application/src/main/kotlin/tilbakekreving/application/service/iverksett/IverksettTilbakekrevingService.kt:43–145`; `tilbakekreving/application/src/main/kotlin/tilbakekreving/application/service/consumer/GenererVedtaksbrevTilbakekrevingKonsument.kt:61–67,118–185`; `tilbakekreving/infrastructure/src/main/kotlin/tilbakekreving/infrastructure/client/TilbakekrevingProxyClient.kt:61–110`. |
| K15 | `service/src/main/kotlin/no/nav/su/se/bakover/service/sak/SakServiceImpl.kt:178–265`; `service/src/main/kotlin/no/nav/su/se/bakover/service/antivirus/VirusFileScannerService.kt:19–44`; `service/src/main/kotlin/no/nav/su/se/bakover/service/regoppslag/RegoppslagService.kt:25–35`. |
| K16 | `web/src/main/kotlin/no/nav/su/se/bakover/web/AuthenticationConfig.kt:27–45`; `client/src/main/kotlin/no/nav/su/se/bakover/client/azure/AzureClient.kt:19–51`. |
| K17 | `web/src/main/kotlin/no/nav/su/se/bakover/web/services/ServiceBuilder.kt:503–518`; `service/src/main/kotlin/no/nav/su/se/bakover/service/brev/PdfByggerService.kt:30–85`; `dokument/domain/src/main/kotlin/dokument/domain/generer/DokumentMal.kt:32–216`. |

Volumtolkningen bygger også på `database/.../statistikk/SøknadStatistikkRepoImpl.kt`, `domain/.../statistikk/SakStatistikk.kt` og `service/.../statistikk/StatistikkVisningService.kt`. De er kontrollert som grunnlag for spørringene og tolkningen, ikke som bevis på produksjonsdataenes fullstendighet.

Dette utkastet legger ikke til logg eller annen dokumentasjon; repositoryets historikkregler krever ikke en egen loggoppføring for et ordinært arbeidsdokument.
