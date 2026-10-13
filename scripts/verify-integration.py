#!/usr/bin/env python3
from runner_contract import request_v1
"""Black-box integration checks. Requires running Spring Boot and Judge0."""
import copy
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE = os.environ.get("CODELOBBY_API_URL", "http://127.0.0.1:8081").rstrip("/")
KEY = os.environ.get("CODELOBBY_INTERNAL_API_KEY", "")
if len(KEY) < 32:
    sys.exit("Set CODELOBBY_INTERNAL_API_KEY to the same secret as the backend (minimum 32 characters).")
ROOT = Path(__file__).resolve().parent.parent
checks = []


def request(path, body=None, authenticated=True):
    headers = {"Content-Type": "application/json"}
    if authenticated:
        headers["X-CodeLobby-Internal-Key"] = KEY
    req = urllib.request.Request(BASE + "/internal/v1" + path,
                                 data=None if body is None else json.dumps(request_v1(body)).encode(), headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=180) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def check(name, action):
    started = time.monotonic()
    try:
        action()
        checks.append({"name": name, "passed": True, "seconds": round(time.monotonic() - started, 3)})
        print("PASS", name, flush=True)
    except Exception as error:
        checks.append({"name": name, "passed": False, "error": str(error)})
        print("FAIL", name, str(error), flush=True)


def require(condition, detail):
    if not condition:
        raise AssertionError(detail)


vowels = json.loads((ROOT / "examples/integration/vowel-evaluation.json").read_text())


def evaluate(body, status, passed=None):
    code, response = request("/evaluations", body)
    require(code == 200, f"HTTP {code}: {response}")
    require(response.get("submissionId") == body["submissionId"], "submissionId mismatch")
    result = response["result"]
    require(result["status"] == status, f"Expected {status}, received {result['status']}")
    require(result["totalTests"] == len(body["testCases"]), "totalTests mismatch")
    if passed is not None:
        require(result["passedTests"] == passed, "passedTests mismatch")
    for case, output in zip(body["testCases"], result.get("testResults", [])):
        if case["visibility"] == "HIDDEN":
            require(output["output"] is None and output["stderr"] is None, "Hidden output exposed")
    benchmark = result.get("benchmark")
    if status == "ACCEPTED":
        require(benchmark is not None and benchmark["status"] == "COMPLETED", "Benchmark not completed")
        require(benchmark["warmupRuns"] == 2 and benchmark["measuredRuns"] == 5, "Incorrect run count")
        require(result["runtimeMs"] is not None and result["peakMemoryBytes"] > 0, "Missing metrics")
    else:
        require(benchmark is None, "Invalid solution benchmarked")


def capability_check():
    code, data = request("/capabilities")
    require(code == 200 and data["apiVersion"] == "v1", "Capabilities unavailable")
    require({"palindrome-length-v1", "vowel-count-v1"}.issubset({a["id"] for a in data["adapters"]}), "Missing adapters")


check("capabilities", capability_check)
check("unauthenticated request rejected", lambda: require(request("/capabilities", authenticated=False)[0] == 401, "Expected 401"))
check("vowel Submit and hidden redaction", lambda: evaluate(vowels, "ACCEPTED", 3))

palindrome = copy.deepcopy(vowels)
palindrome.update(submissionId="verify-palindrome", adapterId="palindrome-length-v1",
                  sourceCode=(ROOT / "examples/palindrome/optimal.cpp").read_text(),
                  testCases=[{"input": s + "\n", "expectedOutput": str(n) + "\n", "visibility": v}
                             for s, n, v in [("babad", 3, "PUBLIC"), ("cbbd", 2, "HIDDEN"), ("", 0, "HIDDEN")]])
check("palindrome Submit", lambda: evaluate(palindrome, "ACCEPTED", 3))


def run_check(adapter, source, stdin, expected):
    body = {"requestId": "verify-run", "language": "CPP", "sourceCode": source,
            "stdin": stdin, "limits": vowels["limits"], "mode": "FUNCTION", "adapterId": adapter}
    if adapter is None:
        body["mode"] = "FULL_PROGRAM"
        body.pop("adapterId")
    code, data = request("/runs", body)
    require(code == 200 and data["requestId"] == body["requestId"], "Run failed")
    require(data["result"]["status"] == "ACCEPTED" and data["result"]["stdout"] == expected, "Run output mismatch")


check("vowel Run", lambda: run_check("vowel-count-v1", vowels["sourceFiles"][0]["content"], "hello\n", "2\n"))
check("palindrome Run", lambda: run_check("palindrome-length-v1", palindrome["sourceCode"], "abba\n", "4\n"))

full = copy.deepcopy(vowels)
full.update(submissionId="verify-full", mode="FULL_PROGRAM",
            sourceCode='#include <iostream>\nint main(){int n;std::cin>>n;std::cout<<n*2;}',
            testCases=[{"input": "3", "expectedOutput": "6", "visibility": "PUBLIC"},
                       {"input": "-2", "expectedOutput": "-4", "visibility": "HIDDEN"}])
full.pop("adapterId")
check("FULL_PROGRAM Submit", lambda: evaluate(full, "ACCEPTED", 2))
check("FULL_PROGRAM Run", lambda: run_check(None, full["sourceCode"], "4", "8"))

for name, change in [
    ("unknown adapter", {"adapterId": "unknown-v1"}),
    ("invalid function input", {"testCases": [{"input": "123\n", "expectedOutput": "0\n", "visibility": "PUBLIC"}]}),
    ("invalid limits", {"limits": {"cpuTimeMs": 999999, "wallTimeMs": 999999, "memoryLimitBytes": 128000000}}),
    ("empty tests", {"testCases": []}),
]:
    body = copy.deepcopy(vowels)
    body.update(change)
    check(name, lambda body=body: require(request("/evaluations", body)[0] == 400, "Expected HTTP 400"))

for name, source, status, passed in [
    ("wrong answer", "int countVowels(const string& s){return 0;}", "WRONG_ANSWER", 1),
    ("compilation error", "int countVowels( {", "COMPILATION_ERROR", 0),
    ("runtime error", "int countVowels(const string& s){throw 1;}", "RUNTIME_ERROR", 0),
    ("time limit", "int countVowels(const string& s){while(true){} return 0;}", "TIME_LIMIT_EXCEEDED", 0),
    ("memory limit", "int countVowels(const string& s){vector<char> x(300000000,1);return x[100];}", "MEMORY_LIMIT_EXCEEDED", 0),
]:
    body = copy.deepcopy(vowels)
    body["sourceCode"] = source
    check(name, lambda body=body, status=status, passed=passed: evaluate(body, status, passed))

report = {"checks": checks, "passed": sum(c["passed"] for c in checks), "total": len(checks)}
output = os.environ.get("CODELOBBY_VERIFY_REPORT")
if output:
    Path(output).write_text(json.dumps(report, indent=2))
print(f"{report['passed']}/{report['total']} checks passed")
sys.exit(0 if report["passed"] == report["total"] else 1)
