import {mountExecutionEditor} from '../src/execution-editor.js';
const root=document.querySelector('#fixture'), report=document.querySelector('#report');
const lines=[];
function check(name, condition){if(!condition) throw Error(name); lines.push(`PASS ${name}`); report.textContent=lines.join('\n');}
const task={id:'one', title:'Test', allowedLanguages:['CPP'], starterCodes:{CPP:'// cpp',PYTHON:'# python',JAVA:'// java'}, publicTestCases:[]};
let count=0, resolve;
const component=mountExecutionEditor(root,{task,onRun:()=>{count++;return new Promise(r=>resolve=r)},onSubmit:async()=>{throw Error('Przykładowy błąd')}});
try {
check('jeden język zablokowany',root.querySelector('#language').disabled);
component.setTask({...task,allowedLanguages:['CPP','PYTHON']});
check('tylko dozwolone języki', [...root.querySelector('#language').options].map(x=>x.value).join(',')==='CPP,PYTHON');
const selection=root.querySelector('#language');
root.querySelector('.cm-content').textContent='// szkic';
root.querySelector('.cm-content').dispatchEvent(new InputEvent('input',{bubbles:true}));
await new Promise(r=>setTimeout(r,50));
const before=component.getDraft().sourceCode;
selection.value='PYTHON';selection.dispatchEvent(new Event('change'));
check('starter Python',component.getDraft().sourceCode==='# python');
selection.value='CPP';selection.dispatchEvent(new Event('change'));
check('zachowany szkic C++',component.getDraft().sourceCode===before && before.includes('szkic'));
root.querySelector('#run').click();root.querySelector('#run').click();
check('blokada równoczesnych Run',count===1 && root.querySelector('#submit').disabled);
let rejected=false;try{component.setTask(task)}catch{rejected=true}check('blokada zmiany podczas żądania',rejected);
resolve({status:'ACCEPTED',stdout:'ok',executionTimeMs:1,memoryBytes:1024});await new Promise(r=>setTimeout(r,0));
check('wynik Run',root.querySelector('#stdout').textContent==='ok');
root.querySelector('#submit').click();await new Promise(r=>setTimeout(r,0));
check('błąd callbacka i odblokowanie',root.querySelector('#stderr').textContent==='Przykładowy błąd' && !root.querySelector('#run').disabled);
component.setTask({...task,id:'two'});check('reset po zmianie zadania',component.getDraft().sourceCode==='// cpp' && component.getDraft().taskId==='two');
rejected=false;try{component.setTask({...task,allowedLanguages:['RUST']})}catch{rejected=true}check('nieobsługiwany język odrzucony',rejected);
component.destroy();component.destroy();check('zamknięcie idempotentne',root.childElementCount===0);
report.textContent+='\nDONE';
}catch(error){report.textContent+='\nFAIL '+error.message;}
