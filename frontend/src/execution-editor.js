import { basicSetup, EditorView } from "codemirror";
import { cpp } from "@codemirror/lang-cpp";
import { python } from "@codemirror/lang-python";
import { java } from "@codemirror/lang-java";
import { Compartment } from "@codemirror/state";
import { oneDark } from "@codemirror/theme-one-dark";
import "./style.css";

const languageProfiles = {
  CPP: [cpp, "solution.cpp", "C++", "C++"],
  PYTHON: [python, "solution.py", "Python", "Python"],
  JAVA: [java, "Solution methods", "Java", "Java"],
};

/** Callbacks communicate with the application's backend, never the internal engine API. */
export function mountExecutionEditor(root, {task, onRun, onSubmit}) {
  if (!(root instanceof HTMLElement) || typeof onRun !== "function" || typeof onSubmit !== "function") {
    throw new Error("Wymagany kontener oraz funkcje onRun i onSubmit.");
  }
root.innerHTML = `
  <main class="page">
    <header class="topbar">
      <a class="brand" href="#" aria-label="CodeLobby">
        <span class="brand-mark">C</span><span>Code<span class="accent">Lobby</span></span>
      </a>
      <div class="engine-status"><span class="status-dot"></span><span id="connection">Run / Submit</span></div>
    </header>
    <section class="intro">
      <p class="eyebrow">EDYTOR KODU</p>
      <h1 id="task-title">Wczytuję zadanie…</h1>
      <p class="subtitle" id="task-description">Kod przechodzi przez Spring Boot do sandboxa wykonawczego.</p>
    </section>
    <section class="workspace" aria-label="Edytor i wynik">
      <div class="editor-column">
        <div class="panel-heading"><div><select id="language" aria-label="Język programowania"></select><strong id="filename">solution.cpp</strong></div><span class="version" id="compiler">C++</span></div>
        <div id="editor"></div>
        <div class="input-panel">
          <label for="stdin">WEJŚCIE STDIN</label>
          <textarea id="stdin" spellcheck="false">babad</textarea>
        </div>
        <div class="input-panel test-panel">
          <label>TESTY JAWNE Z ZADANIA</label>
          <div id="public-tests" class="public-tests">Wczytuję testy…</div>
        </div>
        <div class="actions"><span class="hint">⌘ ↵, aby uruchomić</span><div class="action-buttons"><button id="run" class="secondary"><span>▶</span> Run</button><button id="submit"><span>✓</span> Submit</button></div></div>
      </div>
      <aside class="output-column">
        <div class="panel-heading"><div><strong>Wynik wykonania</strong></div><span id="result-badge" class="badge idle">GOTOWY</span></div>
        <div class="result-body">
          <div class="metrics"><div><span class="metric-label">CZAS CPU</span><strong id="time">—</strong></div><div><span class="metric-label">PAMIĘĆ</span><strong id="memory">—</strong></div></div>
          <div class="output-section"><h2>stdout</h2><pre id="stdout">Uruchom program, aby zobaczyć wynik.</pre></div>
          <div id="test-results" class="test-results" hidden></div>
          <div class="output-section"><h2>stderr</h2><pre id="stderr" class="muted">—</pre></div>
          <div id="details" class="details" hidden></div>
        </div>
      </aside>
    </section>
    <footer id="footer">CodeLobby · Edytor kodu</footer>
  </main>`;

const languageSupport = new Compartment();
const editor = new EditorView({
  doc: "",
  extensions: [basicSetup, languageSupport.of(cpp()), oneDark, EditorView.lineWrapping],
  parent: root.querySelector("#editor"),
});

const button = root.querySelector("#run");
const submitButton = root.querySelector("#submit");
const badge = root.querySelector("#result-badge");
const stdout = root.querySelector("#stdout");
const stderr = root.querySelector("#stderr");
const details = root.querySelector("#details");
const testResults = root.querySelector("#test-results");
const engineLabel = "Execution Engine";
let activeTaskId = null;
let activeLanguage = null;
let busy = false;
let destroyed = false;
let starterCodes = {};
let publicCases = [];
const drafts = {};
const languageSelector = root.querySelector("#language");
languageSelector.addEventListener("change", () => {
  drafts[activeLanguage] = editor.state.doc.toString();
  activeLanguage = languageSelector.value;
  const config = languageProfiles[activeLanguage];
  editor.dispatch({ changes: {from: 0, to: editor.state.doc.length, insert: drafts[activeLanguage] ?? starterCodes[activeLanguage] ?? ""}, effects: languageSupport.reconfigure(config[0]()) });
  root.querySelector("#filename").textContent = config[1];
  root.querySelector("#compiler").textContent = config[2];
});
submitButton.disabled = true;

function configureTask(task) {
  if (busy) throw new Error("Nie można zmienić zadania podczas wykonania.");
  if (!task?.id || !Array.isArray(task.allowedLanguages) || !task.allowedLanguages.length) {
    throw new Error("Zadanie wymaga id i niepustego allowedLanguages.");
  }
  const allowed = [...new Set(task.allowedLanguages)];
  for (const language of allowed) {
    if (!languageProfiles[language] || typeof task.starterCodes?.[language] !== "string") {
      throw new Error(`Brak obsługi lub kodu startowego dla ${language}.`);
    }
  }
  const selected = task.selectedLanguage ?? allowed[0];
  if (!allowed.includes(selected)) throw new Error("Wybrany język nie jest dozwolony.");
  activeTaskId = task.id;
  starterCodes = { ...task.starterCodes };
  publicCases = task.publicTestCases ?? [];
  for (const key of Object.keys(drafts)) delete drafts[key];
  activeLanguage = selected;
  languageSelector.replaceChildren(...allowed.map((language) => {
    const option = document.createElement("option");
    option.value = language;
    option.textContent = languageProfiles[language][3];
    return option;
  }));
  languageSelector.value = selected;
  const profile = languageProfiles[selected];
  editor.dispatch({changes: {from: 0, to: editor.state.doc.length, insert: starterCodes[selected]}, effects: languageSupport.reconfigure(profile[0]())});
  root.querySelector("#filename").textContent = profile[1];
  root.querySelector("#compiler").textContent = profile[2];
  root.querySelector("#task-title").textContent = task.title ?? "Zadanie";
  root.querySelector("#task-description").textContent = task.description ?? "";
  root.querySelector("#stdin").value = task.stdin ?? task.publicTestCases?.[0]?.input ?? "";
  root.querySelector("#public-tests").replaceChildren(...(task.publicTestCases ?? []).map((test, index) => {
    const row = document.createElement("div");
    row.className = "public-test";
    row.textContent = `Test ${index + 1} · wejście: ${test.input.trimEnd()} · oczekiwane: ${test.expectedOutput.trimEnd()}`;
    return row;
  }));
  languageSelector.disabled = allowed.length === 1;
  button.disabled = false;
  submitButton.disabled = false;
  setResult("GOTOWY", "idle");
  stdout.textContent = "Uruchom program, aby zobaczyć wynik.";
  stderr.textContent = "—";
  details.hidden = true;
  testResults.hidden = true;
  root.querySelector("#time").textContent = "—";
  root.querySelector("#memory").textContent = "—";
  root.querySelectorAll(".metric-label")[0].textContent = "CZAS CPU";
  root.querySelectorAll(".metric-label")[1].textContent = "PAMIĘĆ";
}

function formatMemory(bytes) {
  if (typeof bytes !== "number") return "—";
  return bytes >= 1024 * 1024
    ? `${(bytes / (1024 * 1024)).toFixed(1)} MiB`
    : `${Math.round(bytes / 1024)} KiB`;
}

function setResult(label, kind) {
  badge.textContent = label;
  badge.className = `badge ${kind}`;
}

async function runCode(mode = "run") {
  if (busy || destroyed || activeTaskId === null) return;
  busy = true;
  const isSubmission = mode === "submit";
  const activeButton = isSubmission ? submitButton : button;
  button.disabled = true;
  submitButton.disabled = true;
  languageSelector.disabled = true;
  activeButton.innerHTML = '<span class="spinner"></span> Uruchamiam…';
  setResult("W TOKU", "running");
  stdout.textContent = "Kompilowanie i uruchamianie…";
  stderr.textContent = "—";
  stderr.classList.add("muted");
  details.hidden = true;
  testResults.hidden = true;
  root.querySelector("#time").textContent = "—";
  root.querySelector("#memory").textContent = "—";

  try {
    const payload = { taskId: activeTaskId, sourceCode: editor.state.doc.toString(), language: activeLanguage };
    if (!isSubmission) payload.stdin = root.querySelector("#stdin").value;
    const result = await (isSubmission ? onSubmit : onRun)(payload, {onProgress(state) {
      if (!destroyed) {
        setResult(state === "QUEUED" ? "W KOLEJCE" : "OCENIANIE", "running");
        stdout.textContent = state === "QUEUED" ? "Zgłoszenie czeka na ocenę…" : "Sprawdzanie testów i pomiar wydajności…";
      }
    }});
    if (destroyed) return;

    const isCompileError = result.status === "COMPILATION_ERROR";
    const isTimeout = result.status === "TIME_LIMIT_EXCEEDED";
    const isMemoryExceeded = result.status === "MEMORY_LIMIT_EXCEEDED";
    const isSuccess = result.status === "COMPLETED" || result.status === "ACCEPTED";
    const labels = {
      COMPILATION_ERROR: "BŁĄD KOMPILACJI",
      WRONG_ANSWER: "BŁĘDNA ODPOWIEDŹ",
      ACCEPTED: "ACCEPTED",
      TIME_LIMIT_EXCEEDED: "TIMEOUT",
      MEMORY_LIMIT_EXCEEDED: "LIMIT PAMIĘCI",
      RUNTIME_ERROR: "BŁĄD WYKONANIA",
      EXECUTION_ERROR: "BŁĄD USŁUGI",
    };
    setResult(result.status === "ACCEPTED" ? "ACCEPTED" : isSuccess ? "OK" : labels[result.status] || result.status || "BŁĄD", isSuccess ? "success" : "failure");

    stdout.textContent = isSubmission
      ? `Zaliczone testy: ${result.passedTests}/${result.totalTests}`
      : result.stdout || (isCompileError ? "Program nie został uruchomiony." : "(brak wyjścia)");
    if (isSubmission && Array.isArray(result.testResults)) {
      testResults.hidden = false;
      const visible = result.testResults.filter(test => test.visibility === "PUBLIC"
        || (test.visibility == null && publicCases.some((example, index) => (example.testNumber ?? index + 1) === test.testNumber)));
      const publicNumbers = new Set(visible.map(test => test.testNumber));
      const hidden = result.testResults.filter(test => !publicNumbers.has(test.testNumber));
      const rows = visible.map((test, index) => {
        const example = publicCases.find((example, i) => (example.testNumber ?? i + 1) === test.testNumber);
        const row = document.createElement("div");
        row.className = `test-result ${test.status === "ACCEPTED" ? "pass" : "fail"}`;
        const expected = test.expectedOutput ?? example?.expectedOutput;
        row.textContent = `Test jawny ${index + 1}: ${test.status} · oczekiwane: ${expected == null ? "—" : expected.trimEnd() || "(puste)"} · uzyskane: ${test.output == null ? "brak wyniku" : test.output.trimEnd() || "(puste)"}`;
        return row;
      });
      if (hidden.length) {
        const passed = hidden.filter(test => test.status === "ACCEPTED").length;
        const summary = document.createElement("div");
        summary.className = `test-result ${passed === hidden.length ? "pass" : "fail"}`;
        summary.textContent = `Testy ukryte: ${passed}/${hidden.length} zaliczonych${passed === hidden.length ? " · wszystkie poprawne" : ""}`;
        rows.push(summary);
      }
      testResults.replaceChildren(...rows);
    }
    const errorText = isCompileError ? result.compileOutput : isSubmission
      ? (result.testResults || []).filter((test) => test.stderr).map((test) => `Test ${test.testNumber}: ${test.stderr}`).join("\n")
      : result.stderr;
    stderr.textContent = errorText || "(brak błędów)";
    stderr.classList.toggle("muted", !errorText);
    root.querySelector("#time").textContent = isSubmission && result.runtimeMs != null
      ? `${(result.benchmark?.medianRuntimeMs ?? result.runtimeMs).toFixed(3)} ms`
      : result.executionTimeMs != null ? `${result.executionTimeMs} ms` : "—";
    root.querySelector("#memory").textContent = formatMemory(isSubmission ? result.peakMemoryBytes : result.memoryBytes);
    root.querySelectorAll(".metric-label")[0].textContent = isSubmission ? (result.benchmark?.measurementScope === "FUNCTION_CALLS_ELAPSED" ? "MEDIANA CZASU ROZWIĄZANIA" : "MEDIANA CZASU CPU") : "CZAS CPU";
    root.querySelectorAll(".metric-label")[1].textContent = isSubmission ? "PEAK MEMORY" : "PAMIĘĆ";
    const hasNonZeroExitCode = result.exitCode != null && result.exitCode !== 0;
    if (isSubmission && result.benchmark) {
      details.hidden = false;
      details.textContent = result.benchmark.status === "COMPLETED"
        ? `Benchmark: 2 rozgrzewki i 5 pomiarów. Zakres: ${result.benchmark.measurementScope ?? "SUITE_PROCESS_CPU"}. Pamięć dotyczy całego procesu.`
        : "Testy zaliczone, ale benchmark nie został ukończony. Brak wyniku wydajności.";
    } else if (isSubmission && result.message) {
      details.hidden = false;
      details.textContent = `${result.engineStatus ? `${engineLabel}: ${result.engineStatus} · ` : ""}${result.message}`;
    } else if (result.message || result.engineStatus || hasNonZeroExitCode || isTimeout || isMemoryExceeded) {
      details.hidden = false;
      details.textContent = `${result.engineStatus ? `${engineLabel}: ${result.engineStatus} · ` : ""}${result.message || ""}${hasNonZeroExitCode ? ` · kod: ${result.exitCode}` : ""}`;
    }
  } catch (error) {
    if (destroyed) return;
    setResult("BŁĄD ŻĄDANIA", "failure");
    stdout.textContent = "Nie udało się wykonać żądania.";
    stderr.textContent = error.message;
    stderr.classList.remove("muted");
  } finally {
    busy = false;
    languageSelector.disabled = languageSelector.options.length === 1;
    button.disabled = false;
    submitButton.disabled = activeTaskId === null;
    button.innerHTML = "<span>▶</span> Run";
    submitButton.innerHTML = "<span>✓</span> Submit";
  }
}

button.addEventListener("click", () => runCode("run"));
submitButton.addEventListener("click", () => runCode("submit"));
const handleKeydown = (event) => {
  if ((event.metaKey || event.ctrlKey) && event.key === "Enter") {
    event.preventDefault();
    runCode();
  }
};
root.addEventListener("keydown", handleKeydown);
try {
  configureTask(task);
} catch (error) {
  editor.destroy();
  root.replaceChildren();
  throw error;
}
return {
  setTask(nextTask) {
    if (destroyed) throw new Error("Edytor został zamknięty.");
    configureTask(nextTask);
  },
  getDraft() { return {taskId: activeTaskId, language: activeLanguage, sourceCode: editor.state.doc.toString()}; },
  destroy() {
    if (destroyed) return;
    destroyed = true;
    root.removeEventListener("keydown", handleKeydown);
    editor.destroy();
    root.replaceChildren();
  },
};
}
