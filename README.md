# Judge0 CE 1.13.1 — reprodukcja problemu z Docker Desktop

Mały projekt do sprawdzenia, czy oficjalny Judge0 CE 1.13.1 uruchamia i wykonuje zgłoszenie C++ w Docker Desktop. Usługi i wersje obrazów są przypięte w `docker-compose.yml`.

## Wymagania

- Docker Desktop z działającym silnikiem Docker
- `curl`

## Uruchomienie

W terminalu, w tym katalogu:

```sh
cp judge0.conf.example judge0.conf
docker compose up -d
```

Poczekaj na start usług, a następnie sprawdź API:

```sh
curl -sS http://localhost:2358/system_info
```

## Test wykonania C++

```sh
curl -sS -X POST 'http://localhost:2358/submissions?wait=true' \
  -H 'Content-Type: application/json' \
  -d '{"source_code":"#include <iostream>\nint main(){std::cout << \"Judge0 OK\\n\";}","language_id":54,"stdin":""}'
```

Oczekiwany poprawny wynik zawiera status `Accepted` i `stdout` z tekstem `Judge0 OK`.

Jeżeli pojawi się `Internal Error`, zbierz logi workera:

```sh
docker compose logs --tail=100 workers
```

Warto też zapisać architekturę i wersję cgroups widzianą przez Dockera:

```sh
docker info --format 'OS={{.OperatingSystem}} Arch={{.Architecture}} Cgroup={{.CgroupVersion}}'
```

## Zatrzymanie

```sh
docker compose down
```

Nie dodawaj `-v`, jeśli chcesz zachować lokalny wolumen bazy danych.

## Kontekst

Na naszym Macu z Apple Silicon API Judge0 uruchamiało się, ale wykonanie kodu kończyło się `Internal Error`. Log workera wskazywał błąd utworzenia grupy kontrolnej Isolate w `/sys/fs/cgroup/memory/box-.../`. Ten projekt pozwala sprawdzić, czy błąd powtarza się na innej konfiguracji Docker Desktop.

To lokalny PoC do diagnostyki. Nie wystawiaj portu `2358` do Internetu i nie używaj go do przetwarzania prawdziwego kodu studentów.
