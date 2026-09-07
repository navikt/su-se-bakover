# AI-støtte i su-se-bakover

Repositoryet bruker ett kanonisk regelsett og verktøyspesifikke adaptere, slik at
ulike kodeagenter kan dele regler uten at kopier driver fra hverandre.

## Kom raskt i gang

1. Les [`../AGENTS.md`](../AGENTS.md).
2. Arbeid som [SU-ekspert](agents/su-ekspert.agent.md), og les bare relevante deler
   av [kunnskapshuben](su-eksptert.md) og relevante skills.
3. Les relevante filspesifikke instruksjoner og temafiler før du endrer kode.
4. Kontroller planlagt endring mot reglene og rapporter mismatch før du eventuelt ber
   om et avvik.

## Struktur

| Fil eller katalog | Formål |
|---|---|
| [`../AGENTS.md`](../AGENTS.md) | Kanoniske regler for alle AI-agenter |
| [`copilot-instructions.md`](copilot-instructions.md) | Copilot-spesifikk inngang og navigasjon |
| [`instructions/`](instructions/) | Filavgrensede Copilot-instruksjoner |
| [`agents/`](agents/) | SU-standardrolle, støtteprofiler og prosesslæring |
| [`skills/`](skills/) | Behovsaktivert Nav- og teknologikunnskap |
| [`prompts/`](prompts/) | Eksplisitte oppgaver for Ktor og Nais |
| [`hooks/`](hooks/) | Kjørbare kontroller for agentverktøy |
| [`nav-kunnskap/`](nav-kunnskap/) | Kuratert Nav-utvalg, kompatibilitetskontroll og opphav |
| [`su-eksptert.md`](su-eksptert.md) | Kunnskapshub for SU-systemet |
| [`domenekontekst/`](domenekontekst/) | Verifisert domene- og systemkunnskap |
| [`ai-historikk/avvik.jsonl`](ai-historikk/avvik.jsonl) | Godkjente unntak som ikke skaper presedens |
| [`ai-historikk/endringer.jsonl`](ai-historikk/endringer.jsonl) | Endringer i dette AI-oppsettet |
| [`../CLAUDE.md`](../CLAUDE.md) | Importbro fra Claude Code til `AGENTS.md` |
| [`../GEMINI.md`](../GEMINI.md) | Importbro fra Gemini CLI til `AGENTS.md` |

Cursor, OpenAI Codex, nyere JetBrains Junie og støttede GitHub Copilot-flater kan
lese `AGENTS.md` direkte. Claude Code og Gemini CLI bruker sine minimale importbroer.
Ikke opprett kopier under `.cursor/` eller `.junie/` uten et konkret,
verifisert behov.

Offisiell dokumentasjon:

- [GitHub Copilot: repository-instruksjoner](https://docs.github.com/en/copilot/customizing-copilot/adding-repository-custom-instructions-for-github-copilot)
- [OpenAI Codex: `AGENTS.md`](https://developers.openai.com/codex/guides/agents-md/)
- [Claude Code: minne og importer](https://code.claude.com/docs/en/memory)
- [Gemini CLI: `GEMINI.md`](https://geminicli.com/docs/cli/gemini-md/)
- [Cursor: regler og `AGENTS.md`](https://cursor.com/docs/context/rules)
- [JetBrains Junie: prosjektregler](https://www.jetbrains.com/help/junie/customize-guidelines.html)

## Regler, fakta og historikk

Hold innholdet i riktig lag:

- **Regel:** normativ og gjeldende praksis i `AGENTS.md` eller en avgrenset
  instruksjonsfil.
- **Domenefakta:** verifisert oppførsel i en temafil under `domenekontekst/`.
- **Avklaring:** påstand som er uavklart, historisk eller avkreftet i
  `domenekontekst/avklaringer.md`.
- **Prosesslæring:** varig forbedring av agentens arbeidsmåte i
  `agents/su-ekspert.lessons.jsonl`.
- **Avvik:** eksplisitt godkjent unntak for et avgrenset scope i
  `ai-historikk/avvik.jsonl`.
- **Endring:** oppdatering av AI-regler, agentprofiler eller domenedokumentasjon i
  `ai-historikk/endringer.jsonl`.

Et avvik er aldri automatisk en ny standard eller et faktum om systemet. Dersom et
avvik viser seg å være riktig generell praksis, må regelen endres eksplisitt og
endringen registreres separat.

## Kontroll før og etter en AI-endring

Agenten skal:

1. finne reglene som gjelder for berørte filer og flyter
2. kontrollere planlagt løsning mot reglene
3. rapportere relevant mismatch med konsekvens og regelkonformt alternativ
4. få eksplisitt godkjenning før et avvik brukes
5. registrere godkjent avvik før oppgaven avsluttes
6. kontrollere den ferdige endringen mot både reglene og det godkjente scopet

Lov-, personvern- og sikkerhetskrav kan ikke overstyres gjennom avviksloggen.

## JSONL-format

Bruk én gyldig JSON-verdi per linje. Ikke lagre personopplysninger, tokens,
hemmeligheter eller navn på den som godkjente.

En avviksoppføring skal minst ha:

```json
{"date":"YYYY-MM-DD","id":"unik-id","rule":"regel eller filreferanse","scope":["sti eller komponent"],"reason":"hvorfor avviket trengs","decision":"godkjent løsning","consequences":"kjente følger","status":"active","revisit":"hendelse eller dato for ny vurdering","evidence":["kode, test eller beslutningsgrunnlag"]}
```

En endringsoppføring skal minst ha:

```json
{"date":"YYYY-MM-DD","type":"governance-change","summary":"hva som ble endret","reason":"hvorfor","files":["berørte filer"],"evidence":["kode, test eller beslutningsgrunnlag"]}
```

Bruk `status` som `active`, `expired` eller `superseded` for avvik. Gamle
oppføringer skal normalt beholdes og få ny status, ikke slettes.

## Vedlikehold

- Endre kanoniske regler i `AGENTS.md`; ikke rediger adaptere med kopiert innhold.
- Hold leverandøradapterne minimale.
- Oppdater importert Nav-kunnskap manuelt etter kontrollen i
  [`nav-kunnskap/README.md`](nav-kunnskap/README.md).
- Oppdater domenefiler når ny kunnskap er verifisert.
- Promoter bare generell, varig læring fra loggene til en regel.
- Bruk Git som historikk for ordinære kodeendringer. `endringer.jsonl` gjelder bare
  AI-styring og kunnskapsgrunnlaget.
