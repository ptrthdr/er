# Typowe problemy integracji

| Objaw | Sprawdzenie |
| --- | --- |
| Strona nie ładuje zadania | Backend 8081, demo enabled, proxy Vite, właściwy port 5174 |
| 401 / INTERNAL_API_DISABLED | Ten sam klucz co najmniej 32 znaki w obu backendach; nie w froncie |
| Port zajęty | Sprawdź już działające procesy; nie uruchamiaj drugiej instancji silnika |
| /system_info działa, kod nie | API nie potwierdza sandboxa; sprawdź worker logs, digest, cgroups, mount poprawki |
| 400 | Adapter, sygnatura, format wejścia/expected i limity; nie ponawiaj bez poprawy |
| 409 | To samo ID z innym payload; popraw dane lub nadaj świadomie nowe ID |
| 429 | Zgłoszenie nieprzyjęte; odczekaj, ponów tym samym ID |
| 503 przy Run | Sandbox zajęty albo silnik niedostępny; Submit korzysta z kolejki |
| Accepted bez metryk | Benchmark FAILED; nie używaj wyniku w rankingu |
| 404 przy statusie | ID błędne, wynik wygasł lub restart usunął kolejkę; nie czekaj bez końca |
| Wynik Java wygląda jak błąd kompilacji | FUNCTION wymaga static method, bez Solution/Main i imports |

Dla wskazanego projektu Compose sprawdź:
```sh
docker compose -p codelobby-judge0 -f infra/docker-compose.yml -f infra/docker-compose.judge0-mac.yml ps
docker compose -p codelobby-judge0 -f infra/docker-compose.yml -f infra/docker-compose.judge0-mac.yml logs --tail 80 workers
```

Przed wysłaniem logów usuń kod prywatnych zadań, testy ukryte i sekrety.
Nie przekazuj judge0.conf, .env.local ani całych zmiennych środowiskowych.
W zgłoszeniu problemu podaj OS/CPU, wersję Docker, użyty digest, endpoint,
HTTP/status oceny, tryb i język. Pomiń klucz API.
