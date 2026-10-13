# Integracja backendu z runnerem

Kontrakt: [openapi.json](openapi.json) i [integration-contract.md](integration-contract.md).
Działa jako osobna usługa Spring Boot. Nie kopiuj demonstracyjnego backendu
zarządzającego zadaniami do głównego CodeLobby.

1. Uruchom Judge0 według team-quickstart.md (włączone additional_files i compiler_options).
2. Ustaw CODELOBBY_INTERNAL_API_KEY min 32 znaki po obu stronach, poza Git/frontem.
3. Uruchom backend runnera: `cd runner && mvn spring-boot:run`.
4. Pobierz capabilities i startery dla adapterów.
5. Główny backend składa zaufane pliki zadania ze zmianami studenta,
   sprawdza język, uprawnienia oraz pliki read-only i dostarcza taskVersionId.
6. Run wywołuje /internal/v1/runs. Submit /internal/v1/evaluation-jobs.
7. Zachowaj submissionId w swojej bazie; odpytuj job co 700–1000 ms,
   zapisz wynik i usuń szczegóły testów ukrytych z widoku użytkownika.
8. Po restarcie runnera lub 404 obsłuż utracony job zgodnie z polityką backendu.

Przykład multi-file Java: examples/integration/java-run.json i java-submit.json.
Fragmenty funkcji: vowel-evaluation.json. Nowy kontrakt nie akceptuje starych pól.
Źródła FullProgram w jednym katalogu; punkty wejścia Main/main.py/main.cpp.
FUNCTION: jeden plik i zaufany adapter; bez wieloplikowego drivera od backendu.

Główny backend odpowiada za PostgreSQL, JWT, lobby, zadania i rankingi.
Runner ma RAM-ową kolejkę i jedną instancję workera. CI/testy: TESTING.md.
Przykład examples/team-backend pozostaje opcjonalny; właściwa integracja
wymaga rzeczywistego codelobby-backend, którego kodu nie mamy w tym workspace.

Lokalny Docker korzysta z przypiętego, nieoficjalnego obrazu z privileged workerem.
Działające E2E nie jest audytem bezpieczeństwa publicznego wdrożenia.
