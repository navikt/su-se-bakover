# Kontinuitet i SU: støtteunderlag for manuell vurdering

Arbeidsdokument, 11. november 2026. Dette er faktagrunnlag, ikke selve risiko- eller
konsekvensvurderingen. Konsekvensnivåer, prioritet, akseptabelt datatap og tidskrav
skal fylles ut manuelt i en godkjent, skjermet arbeidsflate, ikke i disse filene.
Detaljer og kildehenvisninger står i [kontinuitet-su-avhengigheter.md](kontinuitet-su-avhengigheter.md).
Denne oversikten sammenfatter underlaget, men erstatter ikke kildegrunnlaget.

## Hva skjer når SU er nede?

Ved fullt bortfall kan vi ikke ta imot søknader gjennom SU eller starte og
fortsette saksbehandling. Nye vedtak, utbetalingsendringer, stans, gjenopptak og
klageoversending blir forsinket.

**Oppdrag som allerede er mottatt hos Oppdrag, iverksettes og utbetales videre.**
Dette er opplyst av teamet. Det betyr ikke at SU kan sende nye oppdrag eller
endringer under avbruddet.

**Entra ID/Azure AD er en felles avhengighet for ordinær bruk av SU.**
Bortfall kan hindre innlogging og nødvendige integrasjonskall og dermed stoppe
saksbehandlernes tilgang til saker, behandling og iverksettelse på tvers av flytene.
Gyldige token og mellomlagrede data/nøkler kan la enkelte operasjoner fungere en
stund. Dette er ikke en reserveløsning som sikrer fortsatt saksbehandling.
Ikke alle bakgrunnsjobber trenger Entra i hvert steg.

Når bare en integrasjon er nede, kan virkningen være mer avgrenset:

| Flyt | Hva kan stoppe? | Hva er allerede sikret eller kan fortsette? |
|---|---|---|
| Søknadsmottak | Person-/tilgangsoppslag kan stoppe mottaket før lagring. PDF, journalføring eller Oppgave kan stoppe senere steg. | Søknadsinnholdet lagres før de sistnevnte stegene. Håndterte feil der betyr derfor ikke at søknaden er tapt. |
| Søknadsbehandling | Tilgangskontroll, simulering og nødvendig brevgenerering kan stoppe aktuelle steg. | Beregning bruker registrerte grunnlag og satser, men krever fortsatt fungerende tilgangskontroll. |
| Revurdering | Oppgave kreves ved ordinær opprettelse. Simulering og eventuell brevgenerering kan stoppe senere steg. | Tidligere lagret arbeid består dersom avbruddet ikke innebærer datatap. |
| Stans og gjenopptak | Simulering, kontrollsimulering og sending til Oppdrag må fungere. | Allerede mottatte oppdrag går videre. Endringer som ikke blir sendt, får ikke virkning gjennom denne flyten. |
| Klage | Oppgave og normalt SAF kreves ved opprettelse. Kabal og nødvendig brevgrunnlag kreves ved oversending. | Kabal-bortfall er ikke det samme som bortfall av all intern klagebehandling. |
| Regulering | Relevante oppslag i AAP/Pesys, simulering og sending kan feile. | En regulering kan være lagret som iverksatt uten at utbetalingen er sendt. Denne har egen reparasjonsflyt. |
| Tilbakekreving | Sending av vedtak gjennom SU-proxy må lykkes før iverksettelsen lagres. | Vedtaksbrevet genereres etter iverksettelsen. PDF-bortfall kan derfor forsinke brevet uten å stoppe en allerede lagret iverksettelse. |
| Fritekstbrev og øvrige brevsteg | Generering, journalføring og distribusjon har forskjellige avhengigheter. | Ferdig genererte og lagrede brev trenger ikke genereres på nytt når journalføring eller distribusjon kommer tilbake. |

En lagret søknad kan returneres i backendens sakrespons selv uten journalpost,
oppgave eller behandling. Å lese innholdet og å starte behandling er to forskjellige
ting: behandling krever journalpost og oppgave. Frontendens visning av denne
mellomtilstanden er ikke undersøkt. Tilgangskontrollen må fortsatt fungere.

## Flyttegninger som allerede finnes

Vi gjenbruker systemdokumentasjonens tegninger fremfor å vedlikeholde nye kopier:

| Tegning | Hva den viser |
|---|---|
| [Saksgangen: hovedflyt](../.github/domenekontekst/saksgangen.md#hovedflyt) | Fra søknad til behandling, attestering og iverksettelse, og senere endringer. |
| [Utbetaling: tilstandsmodell](../.github/domenekontekst/utbetaling.md#tilstandsmodell) | Simulering, oversending og mottatt kvittering. |
| [Brev: journalføring og distribusjon](../.github/domenekontekst/brev.md#journalføring-og-distribusjon) | Lagret dokument, journalført dokument og bestilt distribusjon. |
| [Brev: hendelsesbasert dokumentmodell](../.github/domenekontekst/brev.md#hendelsesbasert-dokumentmodell) | Dokumentstegene i de hendelsesbaserte flytene. |

Tegningene er oversikter, ikke en angivelse av hvor lenge avbrudd kan tolereres.
Særlig må tidspunktet for brevgenerering leses sammen med den aktuelle
behandlingsflyten.

## Historisk volum som faktagrunnlag

Tallene bygger på brukerens produksjonsuttrekk for august–oktober 2026 og gjelder
alder og uføre samlet. De kan brukes som underlag for den manuelle vurderingen,
men fastsetter ikke konsekvensnivå eller akseptabelt avbrudd.

| Målt aktivitet | Gjennomsnitt per mandag–fredag-dag |
|---|---:|
| Registrerte søknader | 28,68 |
| Iverksatte søknadsbehandlinger | Omtrent 26,2 |

Registreringer og iverksettelser gjelder ikke nødvendigvis samme søknader, og
behandlingstall er ikke antall unike personer. Øvrige behandlingsflyter kommer i
tillegg. Mandag–fredag er ikke en bemanningskalender. Tallene er ikke målt aktiv
arbeidstid eller dokumentasjon på tilgjengelig reservekapasitet.

## Automatisk etterarbeid som teknisk faktagrunnlag

Jobbene trenger fungerende SU, database og aktuelle integrasjoner. Tidene nedenfor
beskriver kodeoppsettet, ikke fastsatte eller foreslåtte gjenopprettingskrav.

| Etterarbeid | Når forsøkes det igjen? | Viktig begrensning |
|---|---|---|
| Manglende journalpost, oppgave eller behandling for søknad | Hver time, mandag–fredag kl. 06–21. | Søknaden må være eldre enn fem minutter. Jobben reparerer ikke alle endringer på eksisterende oppgaver. |
| Klassisk brev: journalføring og distribusjon | Hvert femte minutt. | Begrenset antall per kjøring. Distribusjon har ventetid mellom forsøk, opptil 24 timer. |
| Hendelsesbasert brevflyt | Hvert minutt. | Utestående hendelser forsøkes igjen. Dette er en annen mekanisme enn den klassiske brevflyten. |
| Tilbakekrevingsbrev og egne oppgavehendelser | Hvert minutt. | Dekker tilbakekrevingsflyten, ikke alle oppgaver i SU. |
| Lagret, usendt reguleringsutbetaling | Hver time, bare i juni. | Jobben velger inneværende års aktuelle reguleringer. |

Automatisk gjenopptak betyr at mye ordinært etterarbeid ikke må gjøres manuelt.
Det betyr ikke at alle feil omfattes, eller at kapasiteten er stor nok til å ta
igjen hele etterslepet umiddelbart.

## Dokumentasjon som kan støtte utfyllingen

Verdivurderingen fra 7. mai 2025 og den gjeldende konsekvensmatrisen brukes fra
sine godkjente lagringssteder. Konkrete vurderinger og verdier fra den skjermede verdivurderingen kopieres ikke hit.
Matrisen brukes manuelt for brukere, ansatte og virksomheten hver for seg og over
tid. Ingen nivåer eller tidsbånd er valgt i dette støtteunderlaget.

Konsekvensnivåene er **Ubetydelig, Lav, Moderat, Alvorlig og Svært alvorlig**.
Navnene er en del av vurderingsrammeverket, ikke en klassifisering av SU eller en
bestemt flyt.

Matrisens prioriteringsramme er gjengitt nedenfor uten at en prioritet er valgt:

| Prioritet | Oppgitt tidsramme |
|---|---|
| 1 | 12–24 timer |
| 2 | 24 timer |
| 3 | 24–48 timer |
| 4 | Inntil én uke |
| 5 | Inntil én måned |

Tidsrammene overlapper ved 24 timer. Grenseoppgangen må avklares ved bruk av
matrisen. Tabellen fastsetter ikke i seg selv RTO eller MTPD.

| Dokumentasjon | Hva den kan underbygge |
|---|---|
| Eksisterende verdivurdering | Tidligere begrunnelser og forutsetninger som de ansvarlige kan vurdere videre. |
| Gjeldende konsekvensmatrise og utfyllingsrutine | Kriterier for konsekvensnivå og prioritering. Riktig nivå velges i den skjermede vurderingen. |
| Prosess- og systemdokumentasjonen lenket over | Hvilke aktiviteter og leveranser flyten består av, og hvilke systemer den trenger. |
| Kodekartleggingen i detaljunderlaget | Hva som krever eksternt svar, hva som allerede er lagret, og hvilke steg som forsøkes igjen automatisk. |
| Historiske aktivitetsvolumer | Målt omfang av mottak og behandling. Ikke konsekvensnivå eller akseptabel avbruddstid. |
| Verifiserte lovkilder og faglige rutiner | Rettslige krav til behandling, klage og utbetaling. Lovfrister er ikke i seg selv tekniske gjenopprettingskrav. |
| Utbetalingskalender og operative kjørefrister | Hvilke datoer og leveranser vurderingen må ta hensyn til. Kalenderen må hentes fra gjeldende operative underlag. |
| Dokumenterte backup-, restore- og gjenopprettingsrutiner | Hvilke data som sikres, referansetidspunkter og målt teknisk gjenopprettingsevne. Dette fastsetter ikke akseptabelt datatap. |
| Bemannings- og beredskapsrutiner, der de finnes | Hvilken kapasitet og hvilke alternative arbeidsmåter som faktisk er tilgjengelige. |

Det er ikke nødvendig å gjennomføre nye undersøkelser av alle disse temaene for
å bruke støtteunderlaget. Henvis til tilgjengelige, gjeldende kilder der de er
relevante, og noter eventuelle mangler i den skjermede arbeidsflaten.

## Kobling til Excel-kolonnene

| Felt i Excel | Støtteunderlag dere kan bruke |
|---|---|
| Prosess, beskrivelse, steg, ID og aktivitet | Flytene over, eksisterende prosessdokumentasjon og steg-ID-ene i detaljunderlaget. |
| Hva andre er avhengig av fra oss | Leveransene i flytene og dokumenterte mottakere, supplert med relevante operative frister. |
| Hva vi er avhengig av | Systemoversikten og kodekartleggingen av nødvendige integrasjoner per steg. |
| Konsekvens for brukere, ansatte og virksomheten | Gjeldende matrise, eksisterende verdivurdering, aktivitetsvolumer, tekniske blokkeringer og faglige frister. Ingen konsekvensceller fylles ut her. |
| Nødvendig leveranse ved avbrudd | Nødvendige aktiviteter og systemer, eksisterende arbeidsrutiner og dokumentert kapasitet. Minimumsnivået bestemmes manuelt. |
| RTO | Dokumenterte gjenopprettingsmålinger, reparasjonsjobbenes oppsett og nødvendige avhengigheter som støtte til et manuelt fastsatt krav. |
| RPO | Oversikt over datagrupper, varig lagring, backup og restore. Backupintervall alene er ikke et akseptert datatap. |
| MTPD | Faglige og rettslige frister, leveranseavhengigheter og vurderingen av hva bortfall innebærer over tid. Grensen fastsettes manuelt. |
| Prioritet/kritikalitet | Gjeldende rutine, avhengigheter og de manuelt fastsatte vurderingene og tidskravene. |
| Forutsetninger og usikkerheter | Kildenes dato, om opplysningen er målt, kodeverifisert eller opplyst av teamet, og eventuelle relevante mangler. |

Forslag og illustrerende tall kan brukes til å sammenligne alternativer i den
manuelle vurderingen. De må merkes som forslag eller regneeksempler, ikke som
anbefalte, besluttede eller nødvendige verdier for SU. Ingen konkret flyt er
tilordnet konsekvensnivå, prioritet, minimumsnivå, RTO, RPO eller MTPD her.

Arbeidstid per sak og årsverk trengs bare hvis vi skal beregne ressursbelastning
eller kapasitet til å ta igjen etterslep. Det er ikke et vilkår for å beskrive
konsekvenser eller fylle ut resten av arket.
