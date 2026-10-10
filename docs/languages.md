# C++, Python i Java

API `language`: CPP / PYTHON / JAVA. Pominięcie języka w demonstracyjnym
Run/Submit oznacza CPP; wewnętrzne API wymaga language jawnie.
Lokalny Judge0 udostępnia GCC 9.2.0 (54), Python 3.8.1 (71), OpenJDK 13.0.1 (62).
Identyfikatorów Judge0 nie wybiera przeglądarka.

Oba adaptery obsługują trzy języki:

| adapterId | C++ | Python | Java |
| --- | --- | --- | --- |
| palindrome-length-v1 | int longestPalindrome(const string& text) | def longest_palindrome(text: str) -> int | static int longestPalindrome(String text) |
| vowel-count-v1 | int countVowels(const string& text) | def count_vowels(text: str) -> int | static int countVowels(String text) |

FUNCTION przyjmuje funkcję/metodę bez main; Java również bez klasy Main i bez
importów (standardowe java.io i java.util dodaje adapter). Python zachowuje
standardowe importy na początku kodu studenta. FULL_PROGRAM przyjmuje cały
program; Java musi mieć public class Main. Input/output i zasady testów pozostają
wspólne. Przykłady: examples/palindrome/optimal.cpp, optimal.py, optimal.java.

Nie porównuj rankingów pomiędzy językami. CPU obejmuje uruchomienie interpretera
lub JVM. Python nie jest kompilowany do kodu maszynowego; kompilacja Javy/C++
odbywa się raz. JVM w każdym z 8 procesów uruchamia się od nowa: rozgrzewki nie
przenoszą stanu JIT między procesami. Ten benchmark mierzy koszt całego procesu,
nie wydajność długo działającej rozgrzanej JVM.

Worker ustawia stałe parametry javac/JVM: SerialGC, 1 aktywny CPU, ograniczone
heap, stos, metaspace, class space i code cache. Heap to tylko część pamięci JVM;
zewnętrzny limit cgroup nadal ogranicza całość. Java może zgłosić OutOfMemoryError
przed cgroup OOM; obecnie jest to Runtime Error, nie zawsze MLE. Nie deklarujemy
pełnej klasyfikacji wszystkich błędów JVM jako MLE. Limity polityki backendu
należy dobierać osobno dla języków. Domyślne limity demo są nadal wspólne.
Kompilacja Javy ma osobny budżet 10 s CPU / 20 s wall / 512 MB. Te zasoby
nie wchodzą do runtime/memory programu; przed wykonaniem powstaje świeży sandbox.
Zestaw sprawdzeń nowych języków: scripts/verify-languages.py (klucz w środowisku).

Backend lobby wybiera i autoryzuje dozwolone języki spośród wspieranych.
Moduł wykonawczy nie przechowuje polityki lobby. Eksperymentalne wersje runtime
są stare; dodanie obsługi nie oznacza gotowości do publicznego wdrożenia.
