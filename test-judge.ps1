
$baseUrl = "http://localhost:2358"

$tests = @(
    @{
        Name = "Java"
        LanguageId = 62
        Code = 'public class Main { public static void main(String[] args) { System.out.println("Judge0 OK"); } }'
    },
    @{
        Name = "C++"
        LanguageId = 54
        Code = '#include <iostream>
int main() { std::cout << "Judge0 OK\n"; }'
    },
    @{
        Name = "Python"
        LanguageId = 71
        Code = 'print("Judge0 OK")'
    }
)

foreach ($test in $tests) {
    Write-Host "`nTesting $($test.Name)..."

    $body = @{
        source_code = $test.Code
        language_id = $test.LanguageId
        stdin = ""
    } | ConvertTo-Json

    try {
        $result = Invoke-RestMethod `
            -Uri "$baseUrl/submissions?wait=true" `
            -Method Post `
            -ContentType "application/json" `
            -Body $body

        $result | Select-Object stdout, stderr, compile_output, message, status, time, memory |
            Format-List

        if ($result.status.id -eq 3 -and $result.stdout.Trim() -eq "Judge0 OK") {
            Write-Host "PASS: $($test.Name)" -ForegroundColor Green
        }
        else {
            Write-Host "FAIL: $($test.Name)" -ForegroundColor Red
        }
    }
    catch {
        Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    }
}
