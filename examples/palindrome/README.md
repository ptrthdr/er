# Palindrom — porównanie rozwiązań

Zadanie: długość najdłuższego **spójnego** palindromu, nie podciągu.
Implementuj `int longestPalindrome(const string& text)` bez `main()`.
Run przyjmuje jeden wiersz małych liter a–z (również pusty).
Zaufany adapter backendu wywołuje funkcję i wypisuje odpowiedź.

- `optimal.cpp`: algorytm Manachera, O(n) czasu i O(n) dodatkowej pamięci.
- `optimal.py`: Manacher w Pythonie, funkcja longest_palindrome.
- `optimal.java`: Manacher w Javie, metoda static longestPalindrome; wklej bez klasy Main.
- `naive.cpp`: wszystkie fragmenty i sprawdzanie każdego od obu końców,
  O(n³) czasu w najgorszym przypadku i O(1) dodatkowej pamięci.

Odśwież stronę, wklej zawartość jednego pliku i kliknij Submit, następnie
powtórz dla drugiego. Oba dostają te same 50 testów i limity całego zestawu.
Submit uruchamia cały zestaw w jednym procesie, a po jego zaliczeniu
wykonuje 2 rozgrzewki i 5 pomiarów w świeżych sandboxach (8 procesów).
Zestaw obejmuje przypadki brzegowe, parzyste i nieparzyste palindromy,
powtarzające się znaki, napisy okresowe i deterministyczne napisy losowe.
Największy napis ma 1537 znaków. Trzy przykłady są jawne, 47 testów ukrytych.

Wersja naiwna może przekroczyć limit czasu na danych powtarzalnych. Wtedy
benchmark jest pomijany, zgodnie z zasadą mierzenia wyłącznie poprawnych
rozwiązań mieszczących się w limitach. Do porównania czasu obu wersji użyj
także Run z krótszym napisem. O(n) nie oznacza mniejszego zużycia pamięci.
