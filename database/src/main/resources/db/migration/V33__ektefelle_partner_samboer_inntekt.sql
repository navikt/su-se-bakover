update søknad set søknadinnhold = jsonb_set(søknadinnhold, '{ektefelle, inntektOgPensjon}', '{
  "pensjon": [],
  "andreYtelserINav": null,
  "forventetInntekt": null,
  "sosialstønadBeløp": null,
  "trygdeytelseIUtlandet": null,
  "andreYtelserINavBeløp": null,
  "tjenerPengerIUtlandetBeløp": null,
  "søktAndreYtelserIkkeBehandletBegrunnelse": null
}') where søknadinnhold #>> '{ektefelle}' IS NOT NULL
