# Mały backend zespołu — przykład integracji

Osobna aplikacja Spring Boot/Java 21, port 8090, dostępna tylko na localhost.
Udaje właściciela zadania: ustala język C++, adapter vowel-count-v1, własne
4 testy (1 jawny, 3 ukryte) i limity. Student przekazuje tylko kod i stdin.
Nie zależy od klas modułu wykonawczego ani jego demonstratora /api.
Komunikuje się przez udokumentowane /internal/v1. Klucz pozostaje na serwerze.

## Uruchomienie

Najpierw uruchom Judge0 i właściwy backend według docs/local-development.md.
Nie jest wymagane CODELOBBY_DEMO_ENABLED na module wykonawczym.
Ustaw CODELOBBY_INTERNAL_API_KEY w terminalu tej aplikacji na ten sam sekret,
który ma moduł wykonawczy. Nie zapisuj klucza w kodzie ani w poleceniach
przekazywanych zespołowi. W katalogu examples/team-backend:

```sh
mvn spring-boot:run
```

Opcjonalnie CODELOBBY_EXECUTION_URL zmienia adres silnika (localhost),
TEAM_BACKEND_PORT zmienia port przykładu. Wymagane JDK 21+ i Maven.

## Przykładowe wywołania

```sh
curl -sS http://127.0.0.1:8090/team-demo/task
curl -sS http://127.0.0.1:8090/team-demo/run \
  -H 'Content-Type: application/json' \
  -d '{"sourceCode":"int countVowels(const string& s){int n=0;for(char c:s)if(string(\"aeiou\").find(c)!=string::npos)n++;return n;}","stdin":"hello\n"}'
curl -sS http://127.0.0.1:8090/team-demo/submissions \
  -H 'Content-Type: application/json' \
  -d '{"sourceCode":"int countVowels(const string& s){int n=0;for(char c:s)if(string(\"aeiou\").find(c)!=string::npos)n++;return n;}"}'
```

Ostatnie wywołanie zwraca 202 i submissionId. Pobierz status, wstawiając ID:

```sh
curl -sS http://127.0.0.1:8090/team-demo/submissions/TUTAJ_ID
```

Powtarzaj co około sekundę aż do FINISHED lub FAILED. Poprawny kod powinien
zwrócić Accepted 4/4 i benchmark. Return 0 powinien dać Wrong Answer 2/4;
błąd składni Compilation Error. Te scenariusze zostały sprawdzone 2026-10-10.
GET task zawiera wyłącznie jawny test; ukryte odpowiedzi nie trafiają do
przeglądarki. Run zwraca samo result, Submit/status pełny obiekt zgłoszenia.
Frontendowy callback onSubmit powinien odpytywać status i zwrócić końcowe result.

## Co kolega zastępuje swoim kodem

DemoController: pobranie zadania, testów, polityki i limitów z własnego backendu.
EngineClient: klient serwerowy API, z timeoutami i kluczem. DemoErrors:
obsługa statusów silnika. Zapis wyników, uprawnienia i bezpieczne ponowienia
należą do prawdziwego backendu. Ten przykład nie ma loginu ani bazy.

ID są zapamiętywane tylko lokalnie (maksymalnie 100, reset przy restarcie).
Niepewny wynik POST po timeout wymaga odzyskania znanego ID; przykład nie
zapewnia trwałej deduplikacji ani odzyskiwania. Nie dodawaj automatycznych
ponowień generujących nowe ID. To demonstrator integracji, nie aplikacja do
publicznego wdrożenia. Dotychczasowy frontend palindromu pozostaje oddzielny.

## Powtórzenie sprawdzeń

Z katalogu głównego projektu, przy działającym przykładzie na 8090:

```sh
python3 scripts/verify-team-backend.py
```

13 sprawdzeń obejmuje zadanie, Run, Submit, benchmark, wyniki jawne i ukryte,
Wrong Answer, Compilation Error, Runtime Error, TLE oraz błędne żądania.
Klient tego skryptu nie używa klucza silnika. Oczekiwany wynik: 13/13.
Raport zapisuje się w /tmp/codelobby-team-backend-report.json.
