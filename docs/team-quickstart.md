# Lokalny start z paczki

Rozpakuj ZIP do nowego katalogu. Wymagane Docker + Compose (kontenery Linux),
JDK 21+, Maven, Node.js/npm; Python 3 do przygotowania i skryptów.
Adresy: silnik 2358, backend 8081, frontend 5174, przykład backendu 8090.

## Konfiguracja, wszystkie platformy

Z katalogu głównego:

```sh
python3 scripts/prepare-local.py
docker compose -p codelobby-judge0 -f infra/docker-compose.yml -f infra/docker-compose.judge0-mac.yml up -d
```

Na Windows użyj `python` zamiast `python3`, uruchom Docker Desktop z kontenerami
Linux (WSL2). Nazwa nakładki zachowana historycznie: wskazuje przypięty
wariant amd64/cgroups, nie gwarantuje obsługi Windows. Na Windows/Linux trzeba
sprawdzić wykonanie sandboxa skryptami. Jeśli mount nie działa, sprawdź dostęp
Docker do katalogu; nie wyłączaj izolacji jako obejścia.
Ustalona nazwa projektu Compose zapobiega przypadkowemu utworzeniu drugiego
stacka. Jeśli kontenery już istnieją, najpierw sprawdź docker compose ls.

Skrypt tworzy lokalne hasła i klucz w .env.local tylko gdy pliki nie istnieją.
Nie uruchamiaj go zamiast aktualizacji ustawień istniejącej bazy: zmiana hasła
w config nie zmienia hasła w już utworzonym Postgres. Nie wysyłaj plików z sekretami.

## Backend — osobny terminal

macOS/Linux, w głównym katalogu:

```sh
set -a
. ./.env.local
set +a
cd runner
mvn spring-boot:run
```

PowerShell, w głównym katalogu:

```powershell
$env:CODELOBBY_INTERNAL_API_KEY = ((Get-Content .env.local | Where-Object { $_ -like 'CODELOBBY_INTERNAL_API_KEY=*' }) -split '=', 2)[1]
Set-Location backend
mvn spring-boot:run
```

Domyślna aplikacja zawiera wyłącznie API integracyjne. W pełnym repo modułu
opcjonalną stronę demo uruchom przez `CODELOBBY_DEMO_ENABLED=true mvn -Pdemo spring-boot:run`.
Paczka do głównego repo nie zawiera źródeł demo i nie używa tego profilu.

## Biblioteka edytora do integracji

```sh
npm --prefix components/code-editor ci
npm --prefix components/code-editor run build:library
```

Zaimportuj JS i CSS z components/code-editor/dist w głównej aplikacji.

## Frontend demonstracyjny — tylko pełne repo modułu, kolejny terminal

```sh
cd examples/demo-frontend
npm ci
npm run dev
```

Otwórz http://127.0.0.1:5174. Kod startowy zawiera TODO — sam w sobie może
nie zaliczać testów. Użyj examples/palindrome/optimal.cpp/.py/.java.

## Przykładowy główny backend

W nowym terminalu wczytaj ten sam klucz z .env.local jak wyżej, następnie:

```sh
cd examples/team-backend
mvn spring-boot:run
```

To aplikacja API na 8090, nie strona graficzna. Scenariusze w jej README.

## Weryfikacja i zatrzymanie

Sprawdzenia wykonuj kolejno z głównego katalogu i kluczem w środowisku:

```sh
python3 scripts/verify-integration.py
python3 scripts/verify-languages.py
python3 scripts/verify-async.py
python3 scripts/verify-team-backend.py
```

Ostatni skrypt wymaga backendu 8090. OpenAPI sprawdza verify-contract.py;
instalacja zależności w openapi-guide.md. Testy wykonują prawdziwy kod,
więc nie prezentuj jednocześnie benchmarku. Nie interpretuj ich jako audytu izolacji.
Frontend/backend zatrzymaj Ctrl+C w odpowiednich terminalach. Kontenery:

```sh
docker compose -p codelobby-judge0 -f infra/docker-compose.yml -f infra/docker-compose.judge0-mac.yml stop
```

Nie usuwaj wolumenów przy zwykłym końcu pracy. Po zmianie workera restart workers,
po zmianie Javy restart backendu. W aplikacji odśwież widok.
