# Dla BACKENDOWCA

Najpierw przeczytaj [przewodnik do przeglądu](docs/BACKEND-REVIEW.md).

Aktualny kontrakt v1: jeden katalog, wiele plików.

Zacznij od [backend-handoff.md](docs/backend-handoff.md) i
[flat-v1-migration.md](docs/flat-v1-migration.md).
Źródło prawdy: [OpenAPI](docs/openapi.json).

Runner przyjmuje sourceFiles [{path,content}], taskVersionId, język,
mode, limity i stdin/testCases. Nie akceptuje sourceCode, files/name,
taskId/taskVersion ani entryPoint w wewnętrznym API.
Główny backend pobiera zaufane pliki/testy, sprawdza język i uprawnienia,
składa zmiany studenta, przechowuje zgłoszenie zgodnie z uzgodnioną strategią, wywołuje runnera,
odpytuje stan i zapisuje wynik. Nie oddaje klucza ani ukrytych testów przeglądarce.

Run synchroniczny, Submit asynchroniczny. Nowy job 202 QUEUED;
replay ukończonego 200. Inne dane z tym samym ID 409.
FINISHED nie oznacza Accepted. Błędy HTTP: {code,message,retryable}.
Kolejka RAM po restarcie znika; recovery i trwały zapis należą do backendu.

Fixture Java: examples/integration/java-run.json i java-submit.json.
Instrukcja uruchomienia: docs/team-quickstart.md.
Testy: docs/TESTING.md. Paczka bez demonstratorów:
python3 scripts/prepare-main-repo.py. Nie ma automatycznej publikacji.
