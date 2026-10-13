# Integracja edytora z frontendem CodeLobby

`components/code-editor/src/execution-editor.js` eksportuje `mountExecutionEditor`.
Komponent nie pobiera zadań i nie zna adresów API. `main.js` jest osobnym
bootstrapem lokalnego demonstratora. Integrator musi mieć zależności CodeMirror
z components/code-editor/package.json oraz obsługę importu CSS w bundlerze.

## Przykład osadzenia

```js
import { mountExecutionEditor } from "./execution-editor.js";

const editor = mountExecutionEditor(document.querySelector("#editor-container"), {
  task: {
    id: "task-123",
    title: "Zadanie",
    description: "Opis zadania",
    allowedLanguages: ["CPP", "PYTHON"],
    selectedLanguage: "CPP", // opcjonalnie; domyślnie pierwszy dozwolony
    starterCodes: {CPP: "...", PYTHON: "..."},
    publicTestCases: [{input: "abc\n", expectedOutput: "3\n"}],
    stdin: "abc\n", // opcjonalnie; domyślnie pierwsze jawne wejście
  },
  onRun: async ({taskId, sourceCode, language, stdin}) => {
    return applicationApi.run({taskId, sourceCode, language, stdin});
  },
  onSubmit: async ({taskId, sourceCode, language}) => {
    return applicationApi.submit({taskId, sourceCode, language});
  },
});
```

`applicationApi` oznacza klienta głównego backendu, dostarczanego przez zespół.
Callbacki zwracają Promise z wynikiem Run/Submit, nie obiekt HTTP Response.
Dla odpowiedzi silnika `{requestId, result}` lub `{submissionId, result}`
klient głównego backendu przekazuje komponentowi samo `result`.
Pola wyniku opisuje integration-contract.md; komponent używa m.in.
executionTimeMs/memoryBytes dla Run oraz runtimeMs/peakMemoryBytes,
passedTests/totalTests/testResults/benchmark dla Submit.
Błąd HTTP powinien być zamieniony na wyjątek z czytelnym message.

## Polityka języków

allowedLanguages jest wymagane i nie może być puste. Dozwolone wartości:
CPP, PYTHON, JAVA. Każdy dozwolony język wymaga starterCodes.
Przy jednym języku selektor jest zablokowany. Przy kilku student wybiera
wyłącznie z przekazanej listy. To ograniczenie interfejsu; główny backend musi
niezależnie egzekwować politykę lobby. Nie przekazuj do komponentu testów
ukrytych, limitów autorytatywnych ani klucza wewnętrznego API.

## Cykl życia

- `getDraft()` zwraca taskId, language i aktualny sourceCode.
- `setTask(task)` ustawia nowe zadanie lub nową politykę i resetuje szkice
  wszystkich języków. Przed zmianą zadania zachowaj szkic, jeśli strona ma go
  przechowywać. Zmiana podczas trwającego żądania jest odrzucana.
- `destroy()` usuwa edytor i jego obsługę zdarzeń. Wywołaj przy odmontowaniu
  widoku. Trwające żądanie sieciowe pozostaje odpowiedzialnością klienta API;
  jego późniejszy wynik nie jest renderowany po destroy.

Szkice między językami są zachowywane w pamięci do zmiany zadania lub zamknięcia.
Komponent blokuje równoczesne Run/Submit; skrót Ctrl/Cmd+Enter działa wewnątrz
jego kontenera. Dostarcza obecną prezentację wyników i CSS, które można później
zastąpić wyglądem docelowej strony. Nie jest jeszcze paczką npm ani komponentem
React/Vue. Integracja z frameworkiem wymaga mount/destroy w jego cyklu życia.

## Ręczne testy przeglądarkowe

Przy działającym Vite otwórz `/dev/editor-check.html`: 11 scenariuszy
komponentu używa kontrolowanych callbacków, bez sandboxa. Wynik końcowy DONE.
Przy działającym demo backendu `/dev/live-check.html` wykonuje rzeczywiste
Run i Submit dla optymalnego palindromu w C++, Pythonie i Javie: 6 scenariuszy,
każdy Submit wymaga 50/50 i ukończonego benchmarku. Wynik końcowy DONE.
Strony są narzędziami lokalnego developmentu; nie są częścią produkcyjnego
wejścia Vite. Uruchamiaj je pojedynczo, aby nie obciążać pomiarów innymi zadaniami.

Asynchroniczny Submit: callback otrzymuje drugi argument `{onProgress}`.
Po przyjęciu zgłoszenia odpytuje endpoint głównego backendu, przekazuje
QUEUED/RUNNING do onProgress i ostatecznie zwraca result. Szczegóły:
[async-submissions.md](async-submissions.md).

## Build biblioteki do głównego frontendu

`npm run build:library` tworzy `dist/execution-editor.js` i `dist/style.css`.
Biblioteka eksportuje mountExecutionEditor, a CodeMirror pozostaje zewnętrzną
zależnością npm. Zaimportuj CSS jawnie przy użyciu gotowego builda.
Style są ograniczone do kontenera .codelobby-execution; nie zmieniają globalnego body.
Paczka do głównego repo nie zawiera main.js ani strony demonstracyjnej.
