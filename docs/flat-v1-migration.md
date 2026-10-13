# Migracja do płaskiego kontraktu v1

Ten kontrakt zastępuje prototypowe HTTP sourceCode oraz rozpoczęte files/name
i taskId/taskVersion. Nie są aliasami. Nie przyjmujemy entryPoint.

```json
{
  "requestId": "r1",
  "taskVersionId": "double-v1",
  "language": "PYTHON",
  "mode": "FULL_PROGRAM",
  "sourceFiles": [
    {"path": "main.py", "content": "from helpers import twice\nprint(twice(int(input())))\n"},
    {"path": "helpers.py", "content": "def twice(n):\n    return n*2\n"}
  ],
  "stdin": "5\n",
  "limits": {"cpuTimeMs":2000,"wallTimeMs":5000,"memoryLimitBytes":128000000}
}
```

Submit: zamiast requestId/stdin podaj submissionId i testCases.
POST /evaluation-jobs zwraca 202 dla nowego/aktywnego joba; 200 przy replay
FINISHED/FAILED. Zawsze odczytaj state oraz result.status.

HTTP error i job.error są obiektem `{code,message,retryable}`;
job.error jest null dla oceny zakończonej normalnym verdictem.
Błędy rozwiązania nie są błędami HTTP. Dodano OUTPUT_LIMIT_EXCEEDED.

Capabilities zawiera files, entryPoints, limits, runtimeVersions,
maxOutputBytesPerStream, adapters i modeProfiles. Wersje wykrywa Judge0,
cache wymaga restartu runnera po zmianie środowiska. Nie interpretuj JDK
hostującego Spring Boot jako wersji Javy studentów.

FULL_PROGRAM jest wieloplikowy, ale płaski. FUNCTION zachowano i zweryfikowano
regresyjnie jako dokładnie jeden plik fragmentu funkcji; adapter generuje driver.
Nie wysyłaj Main klasy z main() w tym trybie. Starter pobierz z adaptera w capabilities.

Na głównym backendzie: zmień DTO/klienta, zachowaj taskVersionId wraz ze
zgłoszeniem, składaj pliki read-only+edytowalne po autoryzacji, nie przekazuj
klucza i ukrytych testów frontendowi. Dane w bazie pozostają odpowiedzialnością
backendu. Jedna instancja runnera i kolejka RAM pozostają ograniczeniem MVP.

Uruchomienie Judge0 wymaga ENABLE_ADDITIONAL_FILES=true i
ENABLE_COMPILER_OPTIONS=true. API Judge0 musi pozostać prywatne; runner nie
udostępnia surowych opcji. Konfiguracja jest w szablonie infra/judge0.conf.example.
Działająca stara konfiguracja z katalogu reprodukcji nie jest automatycznie
aktualizowana przez edycję szablonu; sprawdź mounty i odtwórz server/workers
z aktualnym Compose, zachowując właściwe hasła i bazę.

Źródło prawdy: openapi.json. CI jest przygotowane lokalnie i zacznie działać
po przeniesieniu workflow do repo oraz włączeniu GitHub Actions.
