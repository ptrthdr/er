# Sprawdzenie aktualnej wersji — 2026-10-13

## Wyniki

- Maven verify: 80 testów JUnit/Spring, 0 failures, 0 errors, 0 skipped.
- Rzeczywisty Judge0: source-files 39/39, integration 17/17, languages 8/8, async 12/12 (łącznie 76/76).
- OpenAPI: walidacja offline i test kontraktowy z działającym runnerem zakończone poprawnie.
- Osobny demonstracyjny backend: 13/13 scenariuszy.
- Biblioteka CodeMirror i frontend pokazowy: oba buildy poprawne.
- Przeglądarka: C++, Python, Java — Run stdout=3 oraz Submit 50/50 z ukończonym benchmarkiem (6/6).

## Stan uruchomienia

Przed sprawdzeniem działały runner 8081 i Judge0 2358. Demonstratory były wyłączone.
Uruchomiono mały backend 8090, osobny backend strony 8083 i frontend 5174.
Adres http://127.0.0.1:5174/ jest stroną pokazową. Port 8090 udostępnia API /team-demo;
GET / zwraca 404 zgodnie z brakiem strony w tym backendzie.

## Zakres i ograniczenia

Sprawdzono poprawność, błędy kompilacji/runtime/TLE/MLE, limity wyjścia,
walidację plików i żądań, ukryte wyniki, deduplikację, konflikty i przepełnienie kolejki.
To nie jest audyt bezpieczeństwa, test obciążenia ani pomiar porównawczy wydajności.
Nie sprawdzono integracji z rzeczywistym codelobby-backend ani workflow na GitHubie.
Kolejka nadal jest pamięciowa i traci zgłoszenia po restarcie.
Nie opublikowano zmian ani nie regenerowano wcześniejszych paczek wydania.

Logi tego sprawdzenia: /tmp/current-verify-*.py.log,
/tmp/codelobby-current-junit.log oraz /tmp/current-demo-frontend-build.log.
