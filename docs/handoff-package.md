# Co przekazać zespołowi

Z katalogu głównego:
```sh
python3 scripts/package-handoff.py
```

Powstają deliverables/codelobby-execution-team.zip i plik .sha256.
ZIP zawiera backend, frontend, infra, docs, examples i scripts oraz manifest
SHA-256 plików. Pomija lokalne konfiguracje/sekrety, target/node_modules/dist,
instalatory, checkouty eksperymentalne i binaria. Nie jest wysyłany na GitHub.
Przekaż ZIP i checksum wybranym lokalnym kanałem. Po rozpakowaniu kolega
czyta docs/TEAM-START-HERE.md. .gitignore nie jest mechanizmem redagowania
sekretów — paczka używa osobnej listy dozwolonych katalogów i wykluczeń.

## Obraz Judge0

ZIP nie zawiera obrazu Docker. Compose pobiera mrkushalsm/judge0 przypięty
digestem z nakładki. Te same bity nie oznaczają tej samej wydajności hosta.
Wariant nie został potwierdzony na komputerze drugiego członka zespołu.
Jeśli trzeba prezentować offline, wcześniej można zapisać lokalny obraz:

```sh
docker image save -o judge0-image.tar mrkushalsm/judge0@sha256:d60605ad448453ac3754629bc7121d379cf691e0c091aabbc03ff2886b2626b2
```

Na drugim komputerze: `docker image load -i judge0-image.tar`, następnie sprawdź
czy przypięta referencja jest dostępna. Docker save/load może zachować tag/ID,
ale nie zawsze referencję digestu rejestru; wtedy trzeba również dostęp do
rejestru lub osobną lokalną nakładkę wskazującą zweryfikowany ID obrazu.
Do pełnego offline potrzeba także Postgres/Redis, zależności Maven/npm i JDK.
Nie obiecujemy uruchomienia offline samym ZIP-em źródeł.

Generuj paczkę ponownie po zmianach. Przed przekazaniem sprawdź jej zawartość
oraz aktualne instrukcje; raporty z docs/verification są historyczną dokumentacją
sprawdzeń, nie testem wykonanym na komputerze odbiorcy.
