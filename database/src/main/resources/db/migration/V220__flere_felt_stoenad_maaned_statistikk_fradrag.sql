
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN stonadsklassifisering TEXT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN sats BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN utbetales BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN fradragSum BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN uføregrad BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN alderspensjon BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN alderspensjonEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN arbeidsavklaringspenger BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN arbeidsavklaringspengerEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN arbeidsinntekt BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN arbeidsinntektEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN omstillingsstønad BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN omstillingsstønadEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN avtalefestetPensjon BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN avtalefestetPensjonEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN avtalefestetPensjonPrivat BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN avtalefestetPensjonPrivatEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN bidragEtterEkteskapsloven BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN bidragEtterEkteskapslovenEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN dagpenger BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN dagpengerEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN fosterhjemsgodtgjørelse BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN fosterhjemsgodtgjørelseEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN gjenlevendepensjon BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN gjenlevendepensjonEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN introduksjonsstønad BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN introduksjonsstønadEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN kapitalinntekt BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN kapitalinntektEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN kontantstøtte BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN kontantstøtteEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN kvalifiseringsstønad BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN kvalifiseringsstønadEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN navYtelserTilLivsopphold BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN navYtelserTilLivsoppholdEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN offentligPensjon BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN offentligPensjonEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN privatPensjon BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN privatPensjonEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN sosialstønad BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN sosialstønadEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN statensLånekasse BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN statensLånekasseEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN supplerendeStønad BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN supplerendeStønadEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN sykepenger BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN sykepengerEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN tiltakspenger BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN tiltakspengerEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN ventestønad BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN ventestønadEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN uføretrygd BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN uføretrygdEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN forventetInntekt BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN forventetInntektEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN avkortingUtenlandsopphold BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN avkortingUtenlandsoppholdEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN underMinstenivå BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN underMinstenivåEps BIGINT;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN annet BIGINT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN annetEps BIGINT;
