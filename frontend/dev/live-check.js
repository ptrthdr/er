import {mountExecutionEditor} from '../src/execution-editor.js';
const root=document.querySelector('#fixture'),report=document.querySelector('#report');
const lines=[];
async function post(path,body){const r=await fetch(path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});const data=await r.json();if(!r.ok)throw Error(data.message);return data;}
let result;
const task=await (await fetch('/api/tasks/longest-palindrome')).json();
task.starterCodes={"CPP": "#include <algorithm>\n#include <iostream>\n#include <string>\n#include <vector>\nusing namespace std;\n\n// Manacher: O(n) time, O(n) auxiliary space.\nint longestPalindrome(const string& s) {\n    int n = (int)s.size(), best = 0;\n    vector<int> odd(n), even(n);\n    for (int i = 0, left = 0, right = -1; i < n; ++i) {\n        int k = i > right ? 1 : min(odd[left + right - i], right - i + 1);\n        while (i - k >= 0 && i + k < n && s[i - k] == s[i + k]) ++k;\n        odd[i] = k--;\n        best = max(best, 2 * odd[i] - 1);\n        if (i + k > right) { left = i - k; right = i + k; }\n    }\n    for (int i = 0, left = 0, right = -1; i < n; ++i) {\n        int k = i > right ? 0 : min(even[left + right - i + 1], right - i + 1);\n        while (i - k - 1 >= 0 && i + k < n && s[i - k - 1] == s[i + k]) ++k;\n        even[i] = k--;\n        best = max(best, 2 * even[i]);\n        if (i + k > right) { left = i - k - 1; right = i + k; }\n    }\n    return best;\n}\n", "PYTHON": "def longest_palindrome(text: str) -> int:\n    transformed = \"#\" + \"#\".join(text) + \"#\"\n    radius = [0] * len(transformed)\n    center = right = best = 0\n    for i in range(len(transformed)):\n        if i < right:\n            radius[i] = min(right - i, radius[2 * center - i])\n        while (i - radius[i] - 1 >= 0 and i + radius[i] + 1 < len(transformed)\n               and transformed[i - radius[i] - 1] == transformed[i + radius[i] + 1]):\n            radius[i] += 1\n        if i + radius[i] > right:\n            center, right = i, i + radius[i]\n        best = max(best, radius[i])\n    return best\n", "JAVA": "static int longestPalindrome(String text) {\n    char[] s = new char[text.length() * 2 + 1];\n    for (int i = 0; i < s.length; i++) s[i] = i % 2 == 0 ? '#' : text.charAt(i / 2);\n    int[] radius = new int[s.length];\n    int center = 0, right = 0, best = 0;\n    for (int i = 0; i < s.length; i++) {\n        if (i < right) radius[i] = Math.min(right - i, radius[2 * center - i]);\n        while (i - radius[i] - 1 >= 0 && i + radius[i] + 1 < s.length\n               && s[i - radius[i] - 1] == s[i + radius[i] + 1]) radius[i]++;\n        if (i + radius[i] > right) { center = i; right = i + radius[i]; }\n        best = Math.max(best, radius[i]);\n    }\n    return best;\n}\n"};
const editor=mountExecutionEditor(root,{task:{...task,allowedLanguages:['CPP','PYTHON','JAVA']},onRun:async({sourceCode,language,stdin})=>result=await post('/api/executions',{sourceCode,language,stdin}),onSubmit:async p=>result=await post('/api/submissions',p)});
try{
for(const language of ['CPP','PYTHON','JAVA']){
editor.setTask({...task,allowedLanguages:[language]});
for(const action of ['run','submit']){
result=null;root.querySelector('#'+action).click();
const deadline=Date.now()+180000;
while(root.querySelector('#run').disabled && Date.now()<deadline)await new Promise(r=>setTimeout(r,100));
if(result?.status!=='ACCEPTED')throw Error(language+' '+action+' '+JSON.stringify(result));
if(action==='submit' && (result.passedTests!==50 || result.benchmark?.status!=='COMPLETED'))throw Error('Niepełny Submit');
lines.push('PASS '+language+' '+action+(action==='submit'?' 50/50 benchmark':' stdout='+result.stdout.trim()));report.textContent=lines.join('\n');
}
}
report.textContent+='\nDONE';
}catch(error){report.textContent+='\nFAIL '+error.message;}
