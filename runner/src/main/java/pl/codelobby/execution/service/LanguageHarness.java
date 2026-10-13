package pl.codelobby.execution.service;

import pl.codelobby.execution.api.TrustedEvaluationRequest.Language;

public final class LanguageHarness {
    private LanguageHarness() { }
    public static String wrap(String source, boolean batch, String function, Language language) {
        if (batch) return FunctionBenchmarkHarness.wrap(source, function, language);
        if (language == Language.CPP) return CppLineFunctionHarness.wrap(source, batch, function);
        if (language == Language.PYTHON) {
            String name = CppAdapters.functionFor(function, language);
            return source + "\n\nimport sys\nif __name__ == '__main__':\n"
                    + (batch ? "    count = int(sys.stdin.readline())\n    for _ in range(count):\n        text = sys.stdin.readline().rstrip('\\n')\n        print(" + name + "(text))\n"
                    : "    text = sys.stdin.readline().rstrip('\\n')\n    print(" + name + "(text))\n");
        }
        return "import java.io.*;\nimport java.util.*;\npublic class Main {\n" + source
                + "\npublic static void main(String[] args) throws Exception {\nBufferedReader reader = new BufferedReader(new InputStreamReader(System.in));\n"
                + (batch ? "int count = Integer.parseInt(reader.readLine());\nfor(int i=0;i<count;i++){String text=reader.readLine();System.out.println(" + function + "(text));}\n"
                : "String text=reader.readLine();System.out.println(" + function + "(text == null ? \"\" : text));\n") + "}\n}\n";
    }
}
