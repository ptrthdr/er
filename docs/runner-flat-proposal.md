---
title: "CodeLobby Runner — kontrakt API v1"
subtitle: "MVP: jeden katalog, wiele plików · Specyfikacja do uzgodnienia z Piotrem"
date: "12 października 2026"
lang: pl-PL
mainfont: "Noto Sans"
monofont: "DejaVu Sans Mono"
monofontoptions: "Scale=0.75"
fontsize: 10pt
geometry: margin=20mm
papersize: a4
colorlinks: true
linkcolor: blue
---

**Status dokumentu:** finalna propozycja kontraktu do zatwierdzenia przez zespół w OpenAPI. Przykładowe JSON-y i odpowiedzi poniżej opisują **proponowany interfejs**, a nie funkcjonalność potwierdzoną już w obecnym repozytorium `ptrthdr/er`.

**Najważniejsza decyzja:** MVP obsługuje **wiele plików w jednym, płaskim katalogu roboczym**. Nie obsługuje podkatalogów, pakietów Javy ani importów pakietowych Pythona. Wcześniejszy dokument przewidujący katalogi, `src/main/java`, `include/` i dowolny `entryPoint` zostaje w tym zakresie **zastąpiony** niniejszą wersją.

## 1. Architektura i odpowiedzialności

**Główny `codelobby-backend`:** JWT i użytkownicy; lobby i autoryzacja; zadania i ich wersje; oryginalne pliki i uprawnienia do edycji; testy jawne/ukryte; dozwolone języki; generowanie `submissionId`; trwały stan zgłoszenia i wyniku w PostgreSQL; ranking; komunikacja z runnerem.

**`codelobby-runner` (Piotr):** przyjęcie zaufanego żądania; walidacja struktury plików, limitów i języka; przygotowanie źródeł w jednym katalogu sandboxa; wywołanie Judge0; kompilacja/uruchomienie; wykonanie testów; verdict; benchmark; pamięciowa kolejka Submit; bezpieczna odpowiedź API.

**Frontend:** komunikuje się tylko z głównym backendem. Nie zna klucza runnera, nie wysyła do niego ukrytych testów ani autorytatywnych limitów.

```text
Przeglądarka --> codelobby-backend --> codelobby-runner --> Judge0 / Isolate
              <-- wynik z bazy   <-- wynik wykonania <-- sandbox
                        |
                   PostgreSQL
```

## 2. Model plików: `sourceFiles`

W obu żądaniach Run/Submit przyjmujemy **jedno obowiązkowe pole**:

```json
"sourceFiles": [
  { "path": "Main.java", "content": "public class Main { ... }" },
  { "path": "Calc.java", "content": "class Calculator { ... }" }
]
```

- `path` — sama **nazwa pliku**, względna względem jednego katalogu roboczego; bez `/`, `\\`, katalogów, ścieżek absolutnych i `..`.
- `content` — pełna tekstowa zawartość pliku w UTF-8. Brak binarnych plików, ZIP i załączników w v1.
- Jeden request = jeden język i jeden płaski zestaw plików. `sourceFiles` ma co najmniej 1 element.
- Backend składa pliki dostarczone w zadaniu oraz zmiany użytkownika; runner otrzymuje gotową całość. Runner nie musi znać atrybutów `editable`/`readOnly`.
- Zachowujemy nazwę `path` mimo braku katalogów. W przyszłości rozszerzenie o `src/Main.java` nie zmieni kształtu JSON, ale będzie **osobną zmianą capabilities i implementacji**.
- W nowym kontrakcie **nie używamy** `sourceCode`, `files/name` ani pary `taskId/taskVersion`.

## 3. Reguły języków i punkt wejścia

### Java — `language: "JAVA"`

Przykładowe pliki w jednym katalogu:

```text
Main.java
Calculator.java
Validator.java
```

- Obowiązkowy `Main.java`, zawierający klasę `Main` z publiczną statyczną metodą `main(String[] args)`.
- **Bez deklaracji `package`**, bez podkatalogów i bibliotek pobieranych dynamicznie.
- Dodatkowe `.java` mogą zawierać inne klasy. Publiczna klasa musi odpowiadać nazwie pliku.
- Runner kompiluje **wszystkie** przekazane `.java` i uruchamia `Main` w sandboxie.
- Wersja JDK pochodzi z faktycznie uruchomionego środowiska Judge0 i musi być ujawniona w `/capabilities`; nie obiecujemy Java 21, jeśli obraz używa innej wersji.

### Python — `language: "PYTHON"`

```text
main.py
helpers.py
calculator.py
```

- Obowiązkowy `main.py`, uruchamiany jako skrypt.
- Importy pomiędzy plikami w tym samym katalogu są dozwolone (`import helpers`).
- Bez podkatalogów, pakietów i instalacji zależności z internetu podczas uruchamiania.
- Interpreter i lista dostępnych bibliotek są kontrolowane przez runnera/Judge0.

### C++ — `language: "CPP"`

```text
main.cpp
calculator.cpp
calculator.h
utils.hpp
```

- Obowiązkowy `main.cpp`. Projekt musi mieć **dokładnie jedną** definicję `main()`; brak lub konflikt oznacza błąd kompilacji/linkowania.
- Runner kompiluje wszystkie `.cpp` w katalogu, a pliki `.h`/`.hpp` są wykorzystywane przez `#include "..."`.
- Jedna ustalona wersja standardu i kompilatora, raportowana w capabilities.
- Bez dowolnych poleceń build, Makefile, CMake, flag podawanych przez backend lub skryptów studenta.

**`entryPoint`:** w MVP **nie przesyłamy tego pola**. Jest deterministyczny: Java `Main`, Python `main.py`, C++ `main()` z `main.cpp`. Jeśli później potrzebne będą inne punkty wejścia, można dodać **opcjonalne, walidowane** pole `entryPoint` bez zmieniania `sourceFiles`.

## 4. Limity i bezpieczeństwo plików

Proponowane limity MVP (udostępnione także w `/capabilities`):

| Ograniczenie | Wartość v1 |
|:--|:--|
| Liczba `sourceFiles` | 1–20 |
| Łączny rozmiar `content` | maks. **131 072 bajty UTF-8** (128 KiB) |
| Długość nazwy pojedynczego pliku | maks. 120 znaków ASCII |
| Wielkość surowego body HTTP | maks. **2 000 000 bajtów** dla Run i Submit |
| Liczba przypadków Submit | 1–50 |
| Rozmiar pojedynczego `input` lub `expectedOutput` | maks. 100 000 bajtów UTF-8 |
| `stdout` i `stderr` uruchomionego programu | maks. 64 KiB na strumień i uruchomienie |
| `cpuTimeMs`, `wallTimeMs`, `memoryLimitBytes` | egzekwowane przez runner; maksima zgodne z konfiguracją sandboxa |

Walidacja wymaga: dozwolonych rozszerzeń; niepustych nazw; odrzucania `/`, `\\`, `.`, `..`, NUL, znaków kontrolnych, ścieżek absolutnych, nazw technicznych i duplikatów (w tym kolizji różniących się wyłącznie wielkością liter). **Dane nie mogą być wstawiane do komend powłoki.** Tworzymy wyłącznie zwykłe pliki w przygotowanym obszarze roboczym.

**Limit body musi działać przed nieograniczonym przetwarzaniem JSON-a**, a nie tylko po serializacji obiektu `SubmissionJobs`. Sprawdzać także Run. Przekroczenie -> HTTP 413.

**Limit outputu:** jeśli program przekroczy limit zapisywanych danych, nie może uzyskać ACCEPTED przez porównanie obciętego stdout. Proponowany verdict `OUTPUT_LIMIT_EXCEEDED` (nowy status, jawnie w OpenAPI). Dla kompilatora diagnostyka też jest ograniczana; nadmierny log nie może destabilizować usługi.

Własny worker Judge0 wymaga weryfikacji, czy rzeczywiście potrafi bezpiecznie zmaterializować zestaw plików. Samo zastąpienie pola JSON **nie wystarczy** do uruchomienia wielu plików w aktualnym protokole Judge0.

## 5. Endpoint 1: `POST /internal/v1/runs`

**Cel:** pojedyncze uruchomienie programu z własnym `stdin`; synchroniczne. Tylko główny backend zna adres i wewnętrzny klucz API.

**Request (Java; dwa pliki w jednym katalogu):**

```json
{
  "requestId": "run-123",
  "taskVersionId": "task-42-v3",
  "language": "JAVA",
  "mode": "FULL_PROGRAM",
  "sourceFiles": [
    {
      "path": "Main.java",
      "content": "class Main{public static void main(String[]a){System.out.print(Calc.f());}}"
    },
    {
      "path": "Calc.java",
      "content": "class Calc{static int f(){return new java.util.Scanner(System.in).nextInt()*2;}}"
    }
  ],
  "stdin": "5\n",
  "limits": {
    "cpuTimeMs": 2000,
    "wallTimeMs": 5000,
    "memoryLimitBytes": 128000000
  }
}
```

**Response — HTTP 200** (liczby pomiarów ilustracyjne):

```json
{
  "requestId": "run-123",
  "result": {
    "status": "ACCEPTED",
    "stdout": "10\n",
    "stderr": "",
    "compileOutput": null,
    "executionTimeMs": 23,
    "memoryBytes": 27000000,
    "exitCode": 0,
    "engineStatus": "Accepted",
    "message": null
  }
}
```

Nie wysyłamy `testCases` w Run. Jeśli program zwraca błąd kompilacji, TLE itd., to **wynik wykonania** (`result.status`), a nie błąd komunikacji HTTP. Run nie zapisuje rankingu.

## 6. Endpoint 2: `POST /internal/v1/evaluation-jobs`

**Cel:** asynchroniczny Submit. Backend sam przechowuje testy i przygotowuje zaufany zestaw danych.

**Request:**

```json
{
  "submissionId": "sub-123",
  "taskVersionId": "task-42-v3",
  "language": "JAVA",
  "mode": "FULL_PROGRAM",
  "sourceFiles": [
    {
      "path": "Main.java",
      "content": "class Main{public static void main(String[]a){System.out.print(Calc.f());}}"
    },
    {
      "path": "Calc.java",
      "content": "class Calc{static int f(){return new java.util.Scanner(System.in).nextInt()*2;}}"
    }
  ],
  "limits": {
    "cpuTimeMs": 2000,
    "wallTimeMs": 5000,
    "memoryLimitBytes": 128000000
  },
  "testCases": [
    { "input": "5\n", "expectedOutput": "10\n", "visibility": "PUBLIC" },
    { "input": "-2\n", "expectedOutput": "-4\n", "visibility": "HIDDEN" }
  ]
}
```

**Nowy job — HTTP 202:**

```json
{
  "submissionId": "sub-123",
  "state": "QUEUED",
  "createdAt": "2026-10-12T20:00:00Z",
  "startedAt": null,
  "finishedAt": null,
  "result": null,
  "error": null
}
```

**Deduplikacja:** identyczne `submissionId` i semantycznie te same dane -> zwracamy istniejący job, **bez ponownego wykonania**. Jeśli nadal QUEUED/RUNNING, HTTP 202 i aktualny stan; jeśli FINISHED/FAILED, HTTP 200 i stan końcowy. Ten sam identyfikator z inną treścią -> HTTP 409.

Przy porównywaniu żądań: `sourceFiles` sortujemy **kanonicznie po `path`**, bo ich kolejność w JSON nie zmienia programu. Zawartość plików musi być identyczna bajtowo. Kolejność `testCases` ma znaczenie i nie powinna być sortowana. `taskVersionId`, język, mode i limity też wchodzą do odcisku deduplikacji.

## 7. Endpoint 3: `GET /internal/v1/evaluation-jobs/{submissionId}`

**Cel:** polling stanu i pobranie wyniku. HTTP 200, gdy job istnieje; 404, gdy nieznany, wygasł albo zginął po restarcie.

Stany: **QUEUED -> RUNNING -> FINISHED** lub **FAILED**. Stan `FINISHED` nie oznacza `ACCEPTED` — verdict jest w `result.status`.

**Przykład HTTP 200 z wynikiem:**

```json
{
  "submissionId": "sub-123",
  "state": "FINISHED",
  "createdAt": "2026-10-12T20:00:00Z",
  "startedAt": "2026-10-12T20:00:01Z",
  "finishedAt": "2026-10-12T20:00:18Z",
  "result": {
    "status": "ACCEPTED",
    "passedTests": 2,
    "totalTests": 2,
    "runtimeMs": 17,
    "peakMemoryBytes": 28000000,
    "compileOutput": null,
    "testResults": [
      {
        "testNumber": 1,
        "visibility": "PUBLIC",
        "status": "ACCEPTED",
        "output": "10\n",
        "expectedOutput": "10\n",
        "stderr": ""
      },
      {
        "testNumber": 2,
        "visibility": "HIDDEN",
        "status": "ACCEPTED",
        "output": null,
        "expectedOutput": null,
        "stderr": null
      }
    ],
    "benchmark": {
      "status": "COMPLETED",
      "warmupRuns": 2,
      "measuredRuns": 5,
      "medianRuntimeMs": 17.4,
      "peakMemoryBytes": 28000000,
      "measurementScope": "SUITE_SUM_OF_PROCESSES",
      "measurementTrust": "SANDBOX"
    },
    "message": null
  },
  "error": null
}
```

To jest **odpowiedź wewnętrzna runner -> backend**, a nie gotowy JSON do przeglądarki. Główny backend powinien usunąć szczegóły ukrytych testów i przesłać frontendowi jedynie testy publiczne oraz dozwolone podsumowanie liczby zaliczonych ukrytych testów. Runner nie może ujawniać ukrytego `input`, `expectedOutput`, `stdout`, `stderr` ani danych w komunikatach błędów. Wewnętrzne oznaczenie per-test może służyć backendowi do agregacji, nie należy wystawiać go studentowi.

**Benchmark:** po ACCEPTED 2 rozgrzewki i 5 pomiarów. Gdy benchmark się nie uda, poprawność może zostać ACCEPTED, ale `benchmark.status: "FAILED"`, a `runtimeMs` i `peakMemoryBytes` dla rankingu są `null`. Zachowujemy `measurementScope` i `measurementTrust` — szczególnie przy FUNCTION pomiar wewnątrz procesu może być manipulowany.

## 8. Endpoint 4: `GET /internal/v1/capabilities`

**Cel:** backend dowiaduje się, jakie faktycznie możliwości i ograniczenia oferuje aktualnie wdrożony runner. Klucz API wymagany jak przy pozostałych endpointach.

**Przykładowe HTTP 200** (wersje kompilatorów należy odczytać z wdrożenia; poniżej celowo ich nie zgadujemy):

```json
{
  "apiVersion": "v1",
  "languages": ["JAVA", "PYTHON", "CPP"],
  "modes": ["FULL_PROGRAM"],
  "files": {
    "multiFile": true,
    "supportsDirectories": false,
    "maxFiles": 20,
    "maxSourceBytes": 131072,
    "maxHttpBodyBytes": 2000000
  },
  "entryPoints": {
    "JAVA": "Main",
    "PYTHON": "main.py",
    "CPP": "main.cpp"
  },
  "maxTestCases": 50,
  "maxOutputBytesPerStream": 65536,
  "limits": {
    "maxCpuTimeMs": 2000,
    "maxWallTimeMs": 5000,
    "maxMemoryLimitBytes": 128000000
  },
  "runtimeVersions": {
    "JAVA": "read-from-Judge0",
    "PYTHON": "read-from-Judge0",
    "CPP": "read-from-Judge0"
  }
}
```

**Uwaga:** jeśli na pierwszym etapie działają wyłącznie Java/ FULL_PROGRAM, `/capabilities` deklaruje tylko to, co jest naprawdę gotowe. Python, C++ i FUNCTION dodajemy po przejściu testów; nie publikujemy na zapas możliwości, których runner jeszcze nie potrafi obsłużyć.

## 9. HTTP, błędy i reguły bezpieczeństwa

Nagłówki dla każdego wewnętrznego żądania: `Content-Type: application/json` przy POST oraz `X-CodeLobby-Internal-Key: <sekret>` — sekret wyłącznie między usługami, poza repozytorium, najlepiej w sieci prywatnej i TLS.

**Wspólny format błędu:**

```json
{
  "code": "QUEUE_CAPACITY_EXCEEDED",
  "message": "Queue is full",
  "retryable": true
}
```

| HTTP | Znaczenie | Przykładowy `code` |
|:--|:--|:--|
| 400 | niepoprawne pola, pliki, limity, język | `INVALID_REQUEST`, `INVALID_SOURCE_FILES` |
| 401 | brak/zły klucz wewnętrzny | `UNAUTHORIZED` |
| 404 | brak lub utrata joba | `JOB_NOT_FOUND` |
| 409 | konflikt `submissionId` | `SUBMISSION_ID_CONFLICT` |
| 413 | przekroczony limit body | `PAYLOAD_TOO_LARGE` |
| 429 | pełna kolejka / magazyn | `QUEUE_CAPACITY_EXCEEDED` |
| 503 | niedostępny runner / sandbox | `EXECUTION_UNAVAILABLE` |

Przy 429 i przejściowej 503 pole `retryable` zwykle `true`; przy niepoprawnym JSON, złych plikach i konflikcie ID — `false`. Retry wymaga backoff i limitów prób po stronie backendu. Błąd kompilacji lub zła odpowiedź to **result.status**, nie HTTP 400/500.

Przy synchronicznym Run backend musi mieć timeout odpowiedni dla kompilacji i wykonania. Przy Submit backend zapisuje `submissionId` przed wysłaniem, potem odpytuje stan. Nie wywołuje Judge0 z przeglądarki.

## 10. Przykłady plików Python i C++

**Python — wiele plików, bez pakietów:**

```json
{
  "language": "PYTHON",
  "sourceFiles": [
    {
      "path": "main.py",
      "content": "from helpers import twice\nprint(twice(int(input())))\n"
    },
    {
      "path": "helpers.py",
      "content": "def twice(x):\n    return x * 2\n"
    }
  ]
}
```

**C++ — kilka jednostek kompilacji i nagłówek:**

```json
{
  "language": "CPP",
  "sourceFiles": [
    {
      "path": "main.cpp",
      "content": "#include <cstdio>\n#include \"calc.h\"\nint main(){printf(\"%d\",twice(5));}\n"
    },
    {
      "path": "calc.h",
      "content": "#pragma once\nint twice(int x);\n"
    },
    {
      "path": "calc.cpp",
      "content": "#include \"calc.h\"\nint twice(int x){return x*2;}\n"
    }
  ]
}
```

Są to **fragmenty prezentujące tylko pole `sourceFiles`**. Pełne żądania zawierają też `requestId`/`submissionId`, `taskVersionId`, `mode`, `limits` oraz `stdin` albo `testCases`.

## 11. Funkcje (`FUNCTION`) i zgodność ze starym prototypem

Etap 1 integracji obejmuje **FULL_PROGRAM**. W obecnej implementacji Piotra adaptery FUNCTION przyjmują fragment pojedynczej funkcji, do której worker dodaje kod sterujący. **Nie deklarujemy w v1 obsługi wieloplikowego FUNCTION** bez osobnego testu i ustalonego kontraktu.

Możliwy późniejszy etap: `mode: "FUNCTION"`, `adapterId` wymagane, `sourceFiles` dokładnie 1 plik z fragmentem kodu; driver generowany wyłącznie przez zaufany adapter. Dla FUNCTION `entryPoint` nie występuje. Dopóki nie jest sprawdzone, nie wystawiamy tego w capabilities nowej integracji.

- Dotychczasowe `sourceCode` i `files/name` **nie są aliasami** `sourceFiles`; nowy kontrakt jest zmianą niekompatybilną z lokalnym prototypem. Przed połączeniem zespołu uaktualniamy klientów i dokumentację.
- Stare demonstracyjne `/api/*` mogą pozostać lokalnie, ale nie stanowią kontraktu CodeLobby.
- Historyczne `POST /internal/v1/evaluations` (synchroniczny Submit) można tymczasowo zachować jako legacy; **nowy backend używa** wyłącznie `/evaluation-jobs`.
- `taskVersionId` zastępuje lokalne `taskId/taskVersion`. Runner traktuje go jako nieprzezroczysty identyfikator i nie odpytuje PostgreSQL.

## 12. Kolejka, trwałość, ukryte testy i benchmark

Na MVP można zachować istniejące ograniczenia Piotra: **1 worker**, do **10 oczekujących**, **100 zapisanych snapshotów**, przechowywanie wyników przez **30 minut**, pamięciowa kolejka i deduplikacja. Restart runnera usuwa historię jobów. To nie jest trwałe exactly-once.

Główny backend utrwala zgłoszenie przed wysłaniem, okresowo pobiera wynik, zapisuje go i po restarcie/HTTP 404 podejmuje kontrolowaną decyzję o ponowieniu. Identyczny retry musi używać tego samego ID i danych; nie należy w nieskończoność tworzyć nowych zgłoszeń.

Wyniki jawnych testów mogą zawierać stdout/expectedOutput. Dla ukrytych wyniki w publicznym API backendu są wyłącznie zbiorcze, bez danych testowych. Przy TLE, błędzie uruchomienia lub benchmarku należy redagować wiadomości diagnostyczne, żeby nie ujawnić ukrytego wejścia.

Benchmark uruchamiamy dopiero po zaliczeniu wszystkich przypadków. Wartości `measurementScope` i `measurementTrust` muszą odzwierciedlać **rzeczywisty** charakter pomiaru (szczególnie przy własnym workerze i funkcjach), a nie marketingową etykietę. Rozwiązania bez kompletnego benchmarku nie są klasyfikowane w rankingu wydajności.

## 13. Testy wymagane przed przyjęciem implementacji

**JUnit 5 — jednostkowe:** flat-path walidacja, nazwy, rozszerzenia, duplikaty (także case-insensitive), limity bajtów UTF-8, body 413, wejścia dla Javy/Pythona/C++, mapowanie verdictów, output limit, filtr ukrytych testów, fingerprint z kanoniczną kolejnością plików, konflikt submissionId, daty i semantyka benchmarku.

**Integracyjne Spring Boot z stubem Judge0:** autoryzacja wewnętrznego API, Run/Submit, 202 i polling, stan istniejącego joba, 409, 404, 429, 503, timeout, walidacja JSON, brak wycieku ukrytych danych, zgodność z OpenAPI. Te testy powinny automatycznie działać w GitHub Actions.

**E2E z realnym Judge0:** Java `Main.java + Calculator.java`, Python `main.py + helpers.py`, C++ `main.cpp + calc.cpp + calc.h`; Accepted, Wrong Answer, Compilation Error, Runtime Error, TLE, MLE, zbyt duży output; izolacja, brak sieci i niedozwolone ścieżki. Mogą początkowo działać w kontrolowanym środowisku ręcznie lub w osobnym CI.

Istniejące skrypty `verify-integration.py`, `verify-async.py` i `verify-team-backend.py` Piotra pozostają użyteczne. Nie zastępują jednak testu połączenia **prawdziwego** `codelobby-backend` z runnerem.

## 14. Proponowana kolejność prac / PR

1. **PR: OpenAPI v1.** Zmiana requestów na `sourceFiles`, `taskVersionId`, usunięcie dowolnego `entryPoint`; cztery endpointy, przykłady JSON, statusy, `capabilities`, limity, błędy i kontrakt ukrytych testów.
2. **PR: walidacja i modele.** Jeden katalog, nazwy, 131072 UTF-8 bajtów, body limit przed parserem, canonical fingerprint i testy.
3. **PR: Java FULL_PROGRAM.** Multi-file compile/run przez Judge0, kompilacja jednokrotna w Submit, e2e dwóch plików.
4. **PR: Run/Submit i kolejka.** Bezpieczeństwo API, polling, idempotency, testy integracyjne i CI.
5. **PR: Python i C++.** Wiele plików w jednym katalogu, importy/nagłówki i testy e2e.
6. **PR: FUNCTION i benchmarki.** Oddzielnie, po potwierdzeniu zakresu adapterów i wiarygodności metryk.
7. **Integracja zespołowa:** klient runnera w głównym backendzie, zapis w PostgreSQL, pierwsze prawdziwe zgłoszenie, poprawny wynik w API użytkownika.

**Akceptacja v1:** niech źródłem prawdy będzie `docs/openapi.json`, a opisowe README nie przeczą mu. Zatwierdzamy PR z kontraktem przed zmianami obu usług. Pozwala to rozszerzyć możliwości w przyszłości bez ponownego przepisywania integracji.
