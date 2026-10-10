package pl.codelobby.execution.service;

import pl.codelobby.execution.api.TrustedEvaluationRequest.Language;

/** Measures function calls with monotonic elapsed clocks; sandbox limits remain external. */
final class FunctionBenchmarkHarness {
    private FunctionBenchmarkHarness() { }
    static String wrap(String source, String function, Language language) {
        String name = CppAdapters.functionFor(function, language);
        if (language == Language.PYTHON) return source + """

import sys, time, json
if __name__ == '__main__':
    header = sys.stdin.readline().strip()
    bench = header == 'CODELOBBY_BENCH_V1'
    count = int(sys.stdin.readline()) if bench else int(header)
    inputs = [sys.stdin.readline().rstrip('\\n') for _ in range(count)]
    baseline = None
    samples = []
    for round_index in range(7 if bench else 1):
        begin = time.perf_counter_ns()
        outputs = []
        for index, text in enumerate(inputs):
            if not bench:
                print('CODELOBBY_CASE_V1:' + str(index + 1), file=sys.stderr, flush=True)
            outputs.append(FUNCTION(text))
        elapsed = (time.perf_counter_ns() - begin) / 1e9
        if baseline is not None and outputs != baseline:
            raise RuntimeError('Inconsistent benchmark output')
        baseline = outputs
        if bench and round_index >= 2:
            samples.append(elapsed)
    for output in baseline:
        print(output)
    if bench:
        print('CODELOBBY_TIMES_V1:' + json.dumps(samples), file=sys.stderr)
""".replace("FUNCTION(text)", name + "(text)");
        if (language == Language.JAVA) return "import java.io.*;\nimport java.util.*;\npublic class Main {\n" + source + "\n" + """
public static void main(String[] args) throws Exception {
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    String header = reader.readLine();
    boolean bench = "CODELOBBY_BENCH_V1".equals(header);
    int count = Integer.parseInt(bench ? reader.readLine() : header);
    String[] inputs = new String[count];
    for(int i=0;i<count;i++) inputs[i]=reader.readLine();
    int[] baseline = null;
    double[] samples = new double[5];
    for(int round=0;round<(bench?7:1);round++) {
        int[] outputs = new int[count];
        long begin = System.nanoTime();
        for(int i=0;i<count;i++) {
            if(!bench) { System.err.println("CODELOBBY_CASE_V1:"+(i+1)); System.err.flush(); }
            outputs[i]=FUNCTION(inputs[i]);
        }
        double elapsed = (System.nanoTime()-begin)/1e9;
        if(baseline!=null && !Arrays.equals(baseline,outputs)) throw new IllegalStateException("Inconsistent benchmark output");
        baseline=outputs;
        if(bench && round>=2) samples[round-2]=elapsed;
    }
    for(int output:baseline) System.out.println(output);
    if(bench) System.err.println("CODELOBBY_TIMES_V1:"+Arrays.toString(samples));
}
}
""".replace("FUNCTION(inputs[i])", name + "(inputs[i])");
        return "#include <iostream>\n#include <string>\n#include <vector>\n#include <algorithm>\n#include <chrono>\n#include <limits>\nusing namespace std;\n" + source + "\n" + """
int main() {
    string header; getline(cin,header);
    bool bench=header=="CODELOBBY_BENCH_V1";
    int count;
    if(bench){cin>>count;cin.ignore(numeric_limits<streamsize>::max(),'\\n');}
    else count=stoi(header);
    vector<string> inputs(count);
    for(auto& input:inputs) if(!getline(cin,input)) return 1;
    vector<int> baseline;
    vector<double> samples;
    for(int round=0;round<(bench?7:1);round++) {
        vector<int> outputs(count);
        auto begin=chrono::steady_clock::now();
        for(int i=0;i<count;i++) {
            if(!bench){cerr<<"CODELOBBY_CASE_V1:"<<(i+1)<<'\\n';cerr.flush();}
            outputs[i]=FUNCTION(inputs[i]);
        }
        double elapsed=chrono::duration<double>(chrono::steady_clock::now()-begin).count();
        if(round>0 && outputs!=baseline) return 1;
        baseline=outputs;
        if(bench && round>=2) samples.push_back(elapsed);
    }
    for(int output:baseline) cout<<output<<'\\n';
    if(bench){cerr<<"CODELOBBY_TIMES_V1:[";for(int i=0;i<5;i++){if(i)cerr<<',';cerr<<samples[i];}cerr<<"]\\n";}
}
""".replace("FUNCTION(inputs[i])", name + "(inputs[i])");
    }
}
