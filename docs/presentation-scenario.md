# Scenariusz prezentacji lokalnego MVP (około 10 minut)

## Przed spotkaniem

Uruchom stack według team-quickstart, sprawdź Run/Submit i przygotuj źródła.
Nie zmieniaj konfiguracji/JDK/obrazu w trakcie prezentacji. Zatrzymaj inne
zadania obciążające sandbox. Miej raporty docs/verification jako wyniki
wcześniejszych testów, nie jako dowód bieżącej dostępności.

## Pokaz

1. Zakres modułu: edytor → backend → Judge0, bez bazy lobby/zadań.
2. Palindrom: Run z babad → 3 dla kodu optimal.cpp. Pokaż stdout.
3. Submit optimal.cpp → 50/50, Accepted; pokaż jawne expected/actual i zbiorcze
   zaliczenie ukrytych. Wyjaśnij medianę 5 pomiarów po 2 rozgrzewkach.
4. Naiwny naive.cpp → TLE na obecnym zestawie. Czasy zależą od hosta.
5. Funkcja zwracająca 0 → Wrong Answer, benchmark nie wykonywany.
6. Błąd składni → Compilation Error; throw / pętla → Runtime Error / TLE.
7. Zmień na Python/Java, wklej właściwy optimal.py/.java. Nazwy i sygnatury
   różnią się; metoda Java bez klasy Main. Pamięć to cały proces.
8. Przykładowy backend: własne samogłoski i 4 testy, Run/Submit przez 8090.
   Pokaż własne żądania z README, a następnie pobranie wyniku po ID.
9. Kontrakt OpenAPI, rozdział klucza serwerowego i danych dla przeglądarki.
10. Ograniczenia: RAM kolejki, nieoficjalny obraz, manipulowalny czas funkcji;
    publiczne wdrożenie jest następnym etapem.

## Gotowe źródła błędów dla C++ (palindrom)

Wrong Answer:
```cpp
int longestPalindrome(const string& s) { return 0; }
```
Compilation Error:
```cpp
int longestPalindrome( {
```
Runtime Error:
```cpp
int longestPalindrome(const string& s) { throw 1; }
```
TLE:
```cpp
int longestPalindrome(const string& s) { while (true) {} return 0; }
```

Nie porównuj liczby milisekund z LeetCode. Pokaż porównanie rozwiązań we
własnym języku i środowisku. Gdy usługa jest niedostępna, pokaż diagnostykę,
nie obchodź sandboxa przez wykonanie kodu w Spring Boot.
