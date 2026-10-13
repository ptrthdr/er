# Uruchomienie lokalne i przekazanie zespołowi

## Wymagania

Docker z Compose, JDK 21, Maven, Node.js z npm. Python 3 jest potrzebny tylko
przy ręcznym uruchamianiu skryptów weryfikacyjnych. Na Macu Docker Desktop
musi być uruchomiony. Polecenia poniżej wykonuj w katalogu głównym modułu,
chyba że wskazano inny katalog. Nie są to instrukcje publikacji.

## 1. Konfiguracja Judge0

Skopiuj szablon tylko przy pierwszym uruchomieniu:

```sh
cp -n infra/judge0.conf.example infra/judge0.conf
```

`judge0.conf` jest lokalny i wykluczony z repozytorium. Hasła z szablonu służą
wyłącznie lokalnemu środowisku. Nie zmieniaj COUNT=1.

Na Apple Silicon uruchom przypięty wariant używany przy rozwoju:

```sh
docker compose -f infra/docker-compose.yml -f infra/docker-compose.judge0-mac.yml up -d
```

Nakładka przypina obraz digestem, wymusza amd64 oraz cgroup host workera.
Bazowy Compose zachowuje oficjalny obraz 1.13.1 do osobnych eksperymentów;
nie potwierdzono jego zgodności z każdym hostem, w szczególności cgroups v2.
Poprawka `infra/judge0/isolate_job.rb` jest wymaganą częścią modułu.

## 2. Backend

W katalogu `runner`:

```sh
CODELOBBY_DEMO_ENABLED=true mvn -Pdemo spring-boot:run
```

Domyślnie Judge0, port 8081. Demo należy włączyć do używania przykładowej
strony. Do integracji serwerowej pozostaw demo wyłączone i ustaw własny
`CODELOBBY_INTERNAL_API_KEY` (co najmniej 32 znaki) w środowisku backendu.
Ten sam klucz konfiguruje główny backend, nigdy przeglądarka.

## 3. Frontend

W katalogu `examples/demo-frontend`:

```sh
npm ci
npm run dev
```

Otwórz `http://127.0.0.1:5174`. Proxy wskazuje domyślnie port 8083. Dla runnera demonstracyjnego na 8081 uruchom stronę z `CODELOBBY_BACKEND_URL=http://127.0.0.1:8081 npm run dev`.
Zajęty port powoduje błąd zamiast cichego wyboru innego adresu.

## Konfiguracja

| Zmienna | Domyślnie | Zastosowanie |
| --- | --- | --- |
| EXECUTION_ENGINE | judge0 | Opcjonalnie piston dla porównania |
| SERVER_PORT | 8081 | Port Spring Boot |
| JUDGE0_BASE_URL | http://localhost:2358 | Adres Judge0 |
| CODELOBBY_DEMO_ENABLED | false | Włącza demonstracyjne /api |
| CODELOBBY_INTERNAL_API_KEY | brak | Udostępnia uwierzytelnione /internal/v1 |
| CODELOBBY_BACKEND_URL | http://localhost:8081 | Proxy Vite |

Dla poprzedniego wariantu Piston ustaw `EXECUTION_ENGINE=piston SERVER_PORT=8080`
na backendzie oraz `CODELOBBY_BACKEND_URL=http://localhost:8080` na froncie.
Port frontendu można jawnie zmienić argumentem `--port 5173`.

## Kontrola integracji — uruchamiana świadomie

Przy działającym backendzie i kluczu dostępnym w środowisku:

```sh
python3 scripts/verify-integration.py
python3 scripts/verify-languages.py
```

Są to rzeczywiste wykonania w sandboxie. Sam start API nie potwierdza
poprawnego działania kompilacji ani izolacji. Ten etap porządkowania nie
obejmował ponownego uruchomienia tych sprawdzeń.

## Zatrzymanie

Frontend i Spring Boot zatrzymaj w ich terminalach. Kontenery:

```sh
docker compose -f infra/docker-compose.yml -f infra/docker-compose.judge0-mac.yml stop
```

Zatrzymanie zachowuje dane. Nie usuwaj wolumenów podczas zwykłego zakończenia pracy.

## Co przekazać

Przekaż katalogi runner/components/code-editor/infra/docs/examples/scripts oraz README i
.gitignore. Pomiń lokalny judge0.conf, pliki .env, instalatory work, pobrane
checkouty eksperymentalne, node_modules i wyniki kompilacji. Nie ma potrzeby
przekazywać maszyny UTM. Repozytorium ani publikacja nie są wymagane do lokalnego
przekazania plików.
