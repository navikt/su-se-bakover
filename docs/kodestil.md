# Kodestil (autoformatting / linting)

Benytter oss av spotless: https://github.com/diffplug/spotless/tree/main/plugin-gradle
Som har støtte for forskjellig verktøy som ktlint, ktfmt, diktat, prettier.

## Installer plugin

- IntelliJ: https://plugins.jetbrains.com/plugin/18321-spotless-gradle
  og https://github.com/ragurney/spotless-intellij-gradle
- VS Code: https://marketplace.visualstudio.com/items?itemName=richardwillis.vscode-spotless-gradle

## Hvordan kjøre Spotless

* Hvis du har pluginen; Meny -> Code -> Reformat Code with Spotless
* Det finnes en gradle task; `spotlessApply` som kan keybindes eller knyttes til on-save (må muligens bruke
  FileWatcher-plugin)

## Hvordan diagnosere Spotless

`./gradlew spotlessDiagnose`

## Hvis du fremdeles vil bruke ktlint direkte

* Installer ktlint https://github.com/pinterest/ktlint/releases/ (inntil plugin støtter dette. Kan vurdere legge binæren
  i prosjektet)
* ktlint applyToIDEAProject (denne overstyrer da IntelliJ sine formateringsregler så godt det går.)

## Kjente feil

* Kjør `rm ./.git/hooks/pre-commit ./.git/hooks/pre-push` for å slette gamle hooks hvis den feiler ved commit
