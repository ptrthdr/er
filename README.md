# CodeLobby — moduł wykonywania kodu

Lokalny moduł Spring Boot + Judge0 oraz demonstrator CodeMirror 6.
Obsługiwane języki: **C++, Python, Java**. Judge0 jest domyślnym silnikiem;
Piston pozostaje opcjonalnym wariantem porównawczym.

## Start dla zespołu

**Zacznij od [przewodnika zespołu](docs/TEAM-START-HERE.md).**
Pakiet lokalny przygotowuje `python3 scripts/package-handoff.py`.


Pełna instrukcja: [uruchomienie lokalne](docs/local-development.md).
Domyślne adresy: frontend `http://127.0.0.1:5174`, Spring Boot
`http://localhost:8081`, Judge0 `http://localhost:2358`.

## Struktura

| Katalog | Odpowiedzialność |
| --- | --- |
| backend | API wykonawcze, walidacja, adaptery językowe i interpretacja wyników |
| frontend | Demonstrator edytora, Run i Submit |
| infra | Compose, szablon konfiguracji i wymagany worker Judge0 |
| docs | Kontrakt API, pomiary i instrukcje integracji |
| examples | Przykładowe rozwiązania i żądania integracyjne |
| scripts | Ręczne sprawdzenia integracji i obsługi języków |

## Integracja

Główny backend przekazuje zaufane testy, limity i kod do `/internal/v1/runs`
lub `/internal/v1/evaluations`. Klucz API pozostaje wyłącznie między serwerami.
Moduł nie przechowuje zadań, lobby ani rankingów. Judge0 korzysta z własnego
technicznego Redis/Postgres.

- [Przekazanie backendowi](docs/backend-handoff.md)
- [Kontrakt API](docs/integration-contract.md)
- [OpenAPI 3.1](docs/openapi.json) i [walidacja](docs/openapi-guide.md)
- [Obsługa języków](docs/languages.md)
- [Wykonywanie funkcji](docs/function-evaluation.md)

Submit sprawdza poprawność i dopiero dla poprawnego rozwiązania uruchamia
2 rozgrzewki oraz 5 pomiarów. Wynik zawiera medianę czasu CPU i szczyt pamięci.
Kompilacja odbywa się raz na Submit i nie wchodzi do pomiaru wykonania.
FULL_PROGRAM uruchamia testy w osobnych procesach; FUNCTION przekazuje cały
zestaw wejść do jednego procesu na rundę. Stan globalny może utrzymywać się
między testami w rundzie. Dane testów ukrytych nie trafiają do odpowiedzi
przeglądarki; program w trybie FUNCTION otrzymuje wejścia całego zestawu.

## Stan przekazania

Backend ma osobny kontrakt integracyjny. Frontend ma komponent konfigurowany przez stronę i osobny demonstrator
jednego zadania. Instrukcja: [integracja frontendu](docs/frontend-handoff.md).
API udostępnia pamięciową kolejkę Submit i zachowuje endpoint synchroniczny;
worker wymaga COUNT=1. [Kontrakt kolejki](docs/async-submissions.md). Uruchomienie z czystej kopii na
innym komputerze i obsługa wielu uczestników wymagają dalszej weryfikacji.

Wariant Apple Silicon używa nieoficjalnego obrazu przypiętego digestem oraz
naszej poprawki workera. Zwykły, niezmodyfikowany Judge0 nie obsługuje naszego
protokołu Submit. Konfiguracja służy lokalnej integracji i prezentacji.
