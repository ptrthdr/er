#!/usr/bin/env python3
from runner_contract import request_v1
"""Real sandbox checks for Python/Java adapters and full programs."""
import json
import os
import sys
import urllib.request

base = os.environ.get("CODELOBBY_API_URL", "http://127.0.0.1:8081").rstrip("/")
key = os.environ.get("CODELOBBY_INTERNAL_API_KEY", "")
if len(key) < 32:
    sys.exit("Set CODELOBBY_INTERNAL_API_KEY to the backend secret.")
limits = {"cpuTimeMs": 2000, "wallTimeMs": 5000, "memoryLimitBytes": 128000000}
sources = {
    "PYTHON": ("def count_vowels(text):\n    return sum(c in 'aeiou' for c in text)\n",
               "print(int(input()) * 2)\n"),
    "JAVA": ("static int countVowels(String text){int n=0;for(char c:text.toCharArray())if(\"aeiou\".indexOf(c)>=0)n++;return n;}",
             "public class Main {public static void main(String[] args){java.util.Scanner s=new java.util.Scanner(System.in);System.out.println(s.nextInt()*2);}}")
}
for language, (function, full) in sources.items():
    for mode, source, input_text, output in [("FUNCTION", function, "hello\n", "2\n"),
                                             ("FULL_PROGRAM", full, "3\n", "6\n")]:
        body = {"language": language, "mode": mode, "sourceCode": source, "limits": limits}
        if mode == "FUNCTION":
            body["adapterId"] = "vowel-count-v1"
        for action in ["runs", "evaluations"]:
            payload = dict(body)
            if action == "runs":
                payload.update(requestId="language-check", stdin=input_text)
            else:
                payload.update(submissionId="language-check", testCases=[{
                    "input": input_text, "expectedOutput": output, "visibility": "HIDDEN"}])
            request = urllib.request.Request(base + "/internal/v1/" + action,
                                             data=json.dumps(request_v1(payload)).encode(), headers={
                                                 "Content-Type": "application/json", "X-CodeLobby-Internal-Key": key})
            with urllib.request.urlopen(request, timeout=150) as response:
                result = json.load(response)["result"]
            assert result["status"] == "ACCEPTED", (language, mode, action, result)
            if action == "runs":
                assert result["stdout"] == output, result
            else:
                assert result["passedTests"] == 1, result
                assert result["testResults"][0]["output"] is None, result
                assert result["benchmark"]["status"] == "COMPLETED", result
                assert result["benchmark"]["measuredRuns"] == 5, result
            print("PASS", language, mode, action, flush=True)
print("8/8 checks passed")
