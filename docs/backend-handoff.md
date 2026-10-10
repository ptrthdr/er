# Integracja modułu wykonawczego z backendem CodeLobby

## Uruchomienie lokalne

1. Uruchom Docker Desktop i stack Judge0 według `infra/README.md`.
2. Ustaw `CODELOBBY_INTERNAL_API_KEY` na własny sekret co najmniej 32 znaków.
   Ten sam sekret musi znać główny backend. Nie zapisuj go w Git ani froncie.
3. W katalogu backend uruchom:

```sh
EXECUTION_ENGINE=judge0 mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

Domyślnie demonstracyjne `/api/*` są wyłączone. Włącza je wyłącznie
`CODELOBBY_DEMO_ENABLED=true`. Moduł nie wymaga bazy zadań ani frontendu.
Judge0 ma własne techniczne Redis/Postgres; nie są bazą zadań CodeLobby.

## API v1

Obsługiwane języki: CPP, PYTHON, JAVA. Sygnatury adapterów i zasady JVM opisuje
`languages.md`. Główny backend sprawdza politykę języków dozwolonych w lobby.

Każde wywołanie wymaga `X-CodeLobby-Internal-Key`.

| Endpoint | Przeznaczenie |
| --- | --- |
| GET /internal/v1/capabilities | Dostępne języki, tryby i wersjonowane adaptery |
| POST /internal/v1/runs | Jeden Run: requestId, language, sourceCode, stdin, limits, mode, adapterId |
| POST /internal/v1/evaluations | Submit: submissionId, language, sourceCode, testCases, limits, mode, adapterId |

Przykład gotowego żądania: `examples/integration/vowel-evaluation.json`.
Gotowy zestaw sprawdzeń: `python3 scripts/verify-integration.py` po ustawieniu
klucza w środowisku. Instrukcja i oczekiwane wyniki:
`examples/integration/README.md`.
Odpowiedź Submit: `{submissionId, result}`; Run: `{requestId, result}`.
Dokładne pola wyników, jednostki i błędy opisuje `integration-contract.md`.

## Dodawanie zadań i testów

Backend przechowuje zadania, ich adapterId, starterCode, testy i limity.
Po Submit pobiera zaufane dane i przekazuje je do modułu. Student przesyła
tylko kod i identyfikator zadania. Przeglądarka nie wybiera autorytatywnych
limitów, adapterów ani expectedOutput. Nowy test nie wymaga zmiany modułu.

- FULL_PROGRAM: kompletny program z main(); adapterId pominięty. Dowolny tekst
  stdin/output, testy w osobnych sandboxach, limity per test.
- FUNCTION: kod funkcji bez main(); adapterId obowiązkowy. Testy w jednym
  procesie, limity całego zestawu. Jedna kompilacja, 2 rozgrzewki, 5 pomiarów.
- `palindrome-length-v1`: int longestPalindrome(const string& text).
- `vowel-count-v1`: int countVowels(const string& text), samogłoski a/e/i/o/u.

Oba obecne adaptery przyjmują jeden wiersz małych liter a–z (także pusty)
i zwracają nieujemną liczbę. Nazwy funkcji i kod sterownika pochodzą wyłącznie
z rejestru w module, nie z treści żądania. Nieznany adapter to HTTP 400.
Nowe zadanie o jednej z tych sygnatur może użyć istniejącego adaptera.
Inna sygnatura lub typ wejścia wymaga nowego zaufanego adaptera i wersjonowanego
ID. Nie implementujemy automatycznego generowania dowolnych typów C++.

## Uzgodnienia z zespołem

Odpowiedzi i testy ukryte pozostają na backendzie; nie wysyłać żądań wewnętrznych
z przeglądarki. Wynik funkcji nie zawiera wiarygodnych pomiarów per test:
runtime/memory dotyczą całego procesu. Globals/static utrzymują się między
wywołaniami w rundzie. Awaria procesu przerywa cały zestaw. Dokładny opis:
`function-evaluation.md`.

Nie mieszaj w rankingu różnych trybów, adapterów, zestawów testów i środowisk.
Ustal z zespołem wersję zadania i konfiguracji benchmarku. ID zgłoszenia jest
odsyłane, ale nie zapewnia deduplikacji. API jest synchroniczne; ponowienie może
wykonać kod drugi raz. Docelową kolejkę i zapis wyników należy uzgodnić przed
większą liczbą użytkowników. Obecny worker wymaga COUNT=1.

Starszy PALINDROME_FUNCTION pozostaje kompatybilny jako alias
palindrome-length-v1, ale nowe integracje używają FUNCTION + adapterId.
Zmieniaj sygnatury przez nowe ID adapterów, a nie przez zmianę istniejącego ID.

Repozytorium jest bazą do integracji i prezentacji. Nieoficjalny obraz Judge0,
uprzywilejowane kontenery i zabezpieczenia wymagają osobnej oceny przed publicznym
wdrożeniem. Uruchomienie na komputerze drugiego członka zespołu pozostaje do
wykonania przez zespół; nie zostało tutaj potwierdzone.

## Startery dla każdego języka

Pobierz `/internal/v1/capabilities` i dla wybranego adaptera odczytaj
`languageProfiles[language].starterCode`, `signature` i `sourceShape`.
Zapisz wybrany starter z wersją zadania. Przekaż frontendowi `starterCodes`
i listę języków będącą przecięciem supportedLanguages adaptera oraz polityki
lobby. Starter zawiera TODO, nie jest poprawnym rozwiązaniem referencyjnym.
Starsze pole signature na poziomie adaptera opisuje wyłącznie C++.
Zakres limitów/pomiarów jest dostępny w modeProfiles i measurementScope;
nie porównuj wyników różnych trybów w jednym rankingu.

## Kolejka Submit

Nowe integracje korzystają z `/internal/v1/evaluation-jobs` i pobierania statusu.
Kontrakt, limity oraz ograniczenia pamięciowej kolejki:
[async-submissions.md](async-submissions.md).

Kontrakt maszynowy: [openapi.json](openapi.json). Instrukcja sprawdzenia:
[openapi-guide.md](openapi-guide.md).

## Osobny przykładowy backend

`examples/team-backend` zawiera małą aplikację Spring Boot na localhost:8090.
Ma własne zadanie liczenia samogłosek i 4 testy, a silnik wywołuje wyłącznie
przez API. Instrukcja: [README przykładu](../examples/team-backend/README.md).
