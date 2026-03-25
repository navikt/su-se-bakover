# § 12 Opphold i institusjon m.v. (LOV-2005-04-29-21-§12)

## Hva regulerer paragrafen?

§ 12 regulerer hva som skjer med **supplerende stønad** når mottaker havner på **institusjon** eller i **fengsel**.

## Definisjon av "institusjon"

Med institusjon menes et sted med **heldøgns omsorg og pleie** der driftsutgiftene helt eller delvis dekkes av det offentlige:
- Alders- og sykehjem
- Sykehus og rehabiliteringsinstitusjoner under statlig ansvar


Det finnes ingen minimumsgrense på antall netter – men opphørsregelen krever at oppholdet "har vart i en kalendermåned ut over den måneden da oppholdet tok til", så korte opphold utløser aldri stans.

## Når faller stønaden bort?

Stønaden faller bort når et institusjons- eller fengselsopphold har vart i **en kalendermåned ut over** den måneden da oppholdet tok til:

1. **Innleggelsesmåneden** → stønad løper uendret
2. **Måneden etter** → stønad løper uendret
3. **Derpå følgende måned** → stønad **opphører**

```
Eksempel: Person legges inn 15. mars 2026
─────────────────────────────────────────────────────────────

Tidslinje:     MAR 2026     APR 2026     MAI 2026     JUN 2026
               ─────────    ─────────    ─────────    ─────────
Opphold:       [Innlagt 15.mar ─────────────────────────────>

Stønad bruker:  FULL           FULL        STOPP       STOPP 
               mnd 0        mnd 1        mnd 2+       mnd 3+
               (innlegg.)   (1 mnd       (opphør!)
                             etter)
```

## Konsekvens for ektefelle/samboer/partner

Når den innlagte har EPS som også mottar SU, omregnes EPS fra **par-sats** til **enslig-sats** fra samme måned som den innlagtes stønad stoppes.

```
Eksempel: EPS-omregning når bruker legges inn 15. mars 2026
─────────────────────────────────────────────────────────────

               MAR          APR          MAI          JUN
Bruker:         PAR          PAR          STOPP    STOPP 
EPS:            PAR          PAR          ENSLIG   ENSLIG
                                         ↑
                                         Omregnes til enslig-sats
                                         (samme mnd som bruker stoppes)
```

> NB: Dette skiller seg fra sivilstandsendringer ved dødsfall/separasjon – se § 10 og § 11.

## Flere ikke-sammenhengende opphold

Hvert opphold vurderes **separat**. Flere korte opphold som ikke hver for seg varer en hel kalendermåned utover innleggelsesmåneden, fører ikke til stans:

```
Eksempel: 3 korte sykehusopphold
────────────────────────────────

MAR 2026         APR 2026         MAI 2026
[Inn 5→Ut 10]   [Inn 2→Ut 8]    [Inn 15→Ut 20]

Stønad:  FULL     FULL     FULL 
        ✓ Ingen stans! Hvert opphold er separat.
```

## Etter utskrivning / løslatelse

Det må settes fram **nytt krav** om supplerende stønad. Dersom vilkårene er oppfylt, gis det en ny stønadsperiode på 12 måneder.

## Redusert stønad

Det er **ikke** åpnet for å gi redusert stønad under lengre institusjons- eller fengselsopphold. Stønaden enten løper fullt eller er stoppet.

## Hvordan dette er implementert i koden

### Vilkårsmodell

- [`InstitusjonsoppholdVilkår`](domain/src/main/kotlin/no/nav/su/se/bakover/domain/vilkår/InstitusjonsoppholdVilkår.kt) – sealed interface med `IkkeVurdert` og `Vurdert`
- [`VurderingsperiodeInstitusjonsopphold`](domain/src/main/kotlin/no/nav/su/se/bakover/domain/vilkår/VurderingsperiodeInstitusjonsopphold.kt) – en periode med vurdering (Innvilget/Avslag/Uavklart)
- `Vurdering.Innvilget` = person er **ikke** på institusjon (vilkår oppfylt, stønad innvilges)
- `Vurdering.Avslag` = person **er** på institusjon → avslagsgrunn `INNLAGT_PÅ_INSTITUSJON` (§ 12)

### Hendelsesbasert varsling

1. `EksternInstitusjonsoppholdKonsument` mottar hendelser fra NAVs institusjonsregister (Kafka)
2. Typer: `INNMELDING`, `OPPDATERING`, `UTMELDING`, `ANNULERING`
3. `OpprettOppgaverForInstitusjonsoppholdshendelser` oppretter oppgaver i Gosys
4. Saksbehandler vurderer manuelt om vilkåret er oppfylt

### Manuell vurdering – ikke automatisk

Kalendermåned-regelen (stopp etter 1 mnd utover innleggelsesmåneden) beregnes **ikke** automatisk av systemet. Saksbehandler er ansvarlig for å vurdere dette og sette riktig periode og resultat.

```
Ekstern hendelse (Kafka)         Saksbehandler vurderer       Konsekvens
┌──────────────────────┐        ┌─────────────────────┐      ┌──────────────────┐
│ INNMELDING/           │──────>│ Oppgave i Gosys     │─────>│ Vilkår: Avslag   │
│ OPPDATERING/          │       │ → Manuell vurdering │      │ → Opphør/Avslag  │
│ UTMELDING/ANNULERING  │       │   av §12            │      │   ref. §12       │
└──────────────────────┘        └─────────────────────┘      └──────────────────┘
```


