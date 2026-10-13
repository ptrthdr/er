# Weryfikacja runnera

## JUnit + Spring (bez Dockera)

`mvn -f runner/pom.xml clean verify`

Testy obejmują SourceBundle, limity UTF-8, kolizje nazw, raw HTTP body przed
parserem, statusy, outputy, ukryte dane, benchmark, kolejkę, fingerprint,
uwierzytelnienie oraz kontrolery pełnego kontekstu Spring. Klient Judge0 jest
mockowany w testach Spring; te testy nie potwierdzają izolacji sandboxa.
Raporty runner/target/surefire-reports.
Mockito mock-maker-subclass nie wymaga dynamicznego agenta.

## Kontrakt offline

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install -r scripts/contract-requirements.txt
python scripts/validate-contract.py
```

Sprawdza OpenAPI i przykłady bez sekretów/usług. Workflow
.github/workflows/execution-ci.yml uruchamia testy, walidację kontraktu
oraz build biblioteki edytora. Nie uruchamia uprzywilejowanego sandboxa na PR.
Do monorepo trzeba dopasować ścieżki workflow. Nic nie jest publikowane automatycznie.

## E2E z realnym Judge0

Uruchom aktualny worker/konfigurację i runner. Ustaw
CODELOBBY_INTERNAL_API_KEY oraz ewentualnie CODELOBBY_API_URL.
Wykonuj kolejno:

```sh
python3 scripts/verify-source-files.py
python3 scripts/verify-integration.py
python3 scripts/verify-languages.py
python3 scripts/verify-async.py
python scripts/verify-contract.py
```

Pierwszy test obejmuje Java Main+Calc, Python main+helpers, C++ main+calc+header,
kompilację nieużywanej klasy Java, canonical replay, konflikt wersji, niedozwolone
nazwy, case collisions, odtwarzanie plików między testami, output limit i próbę
połączenia wychodzącego. Pozostałe sprawdzają błędy kompilacji/runtime,
TLE/MLE, regresję FUNCTION i kontrakt odpowiedzi.
verify-contract wymaga zależności Python z contract-requirements.txt.
Skrypty kończą się błędem przy nieudanym scenariuszu.

Testy blokady sieci lub czystości plików są ograniczonymi scenariuszami,
nie audytem izolacji. Nie potwierdzają trwałości kolejki ani skalowania replik.
Nie testujemy tu prawdziwego codelobby-backend, ponieważ nie jest częścią workspace.

## Edytor

`npm --prefix components/code-editor ci` i `npm --prefix components/code-editor run build:library`.
Buduje JS/CSS do integracji; nie zastępuje testów zachowania w przeglądarce.
Komponent pozostał funkcyjny z callbackami. Obsługa wielu plików w runnerze
nie oznacza automatycznie drzewa plików w demonstracyjnej stronie.
