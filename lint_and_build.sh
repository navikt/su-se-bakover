#!/bin/bash
expectedKtlintVersion=$(grep "val ktlintVersion" build.gradle.kts | sed 's/\(^.*"\)\(.*\)\(".*$\)/\2/')
if ! command -v ktlint &>/dev/null; then
  echo "Using ./gradlew ktlintFormat"
  echo "You can increase the ktlint speed by installing https://github.com/pinterest/ktlint/releases/tag/${expectedKtlintVersion}"
  ./gradlew ktlintFormat
else
  actualKtlintVersion=$(ktlint --version)
  if [[ "$actualKtlintVersion" != "$expectedKtlintVersion" ]]; then
    echo "Using ./gradlew ktlintFormat"
    echo "Your ktlint version (${actualKtlintVersion}) does not match this project's. https://github.com/pinterest/ktlint/releases/tag/${expectedKtlintVersion}"
    ./gradlew ktlintFormat
  else
    echo "Using $(which ktlint) instead of ./gradlew ktlintFormat for faster lint speed."
    actualKtlintVersion=$(ktlint --version)
    ktlint -F
  fi
fi
echo "Linting complete, running: ./gradlew clean build"
./gradlew build
