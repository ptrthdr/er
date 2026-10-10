import { mountExecutionEditor } from "./execution-editor.js";

const root = document.querySelector("#app");

async function post(path, payload) {
  const response = await fetch(path, {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify(payload),
  });
  const result = await response.json();
  if (!response.ok) throw new Error(result.message ?? `Błąd API (${response.status})`);
  return result;
}

async function submitJob(payload, {onProgress}) {
  let job = await post("/api/submission-jobs", payload);
  while (job.state === "QUEUED" || job.state === "RUNNING") {
    onProgress(job.state);
    await new Promise(resolve => setTimeout(resolve, 700));
    const response = await fetch(`/api/submission-jobs/${encodeURIComponent(job.submissionId)}`);
    if (!response.ok) throw new Error(`Nie udało się pobrać wyniku (${response.status}). Id: ${job.submissionId}`);
    job = await response.json();
  }
  if (job.state === "FAILED") throw new Error(job.error ?? "Ocena nie powiodła się");
  return job.result;
}

async function startDemo() {
  root.textContent = "Wczytuję zadanie…";
  try {
    const response = await fetch("/api/tasks/longest-palindrome");
    if (!response.ok) throw new Error(`Błąd API (${response.status})`);
    const task = await response.json();
    mountExecutionEditor(root, {
      // The demo enables all implemented languages. The real backend provides lobby policy.
      task: {...task, allowedLanguages: ["CPP", "PYTHON", "JAVA"]},
      onRun: ({sourceCode, language, stdin}) => post("/api/executions", {sourceCode, language, stdin}),
      onSubmit: submitJob,
    });
  } catch (error) {
    root.textContent = `Nie udało się wczytać zadania: ${error.message}. Sprawdź backend i tryb demo.`;
  }
}
startDemo();
