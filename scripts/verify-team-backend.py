"""Black-box checks through the separate mock main backend (no engine API key)."""
import json,os,time,urllib.request,urllib.error
from pathlib import Path
base=os.environ.get('CODELOBBY_TEAM_URL','http://127.0.0.1:8090')
checks=[]
def call(path,body=None):
 req=urllib.request.Request(base+'/team-demo'+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json'})
 try:
  with urllib.request.urlopen(req,timeout=120) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:return e.code,json.load(e)
def check(name,ok):
 assert ok,name
 checks.append(name);print('PASS',name,flush=True)
def submit(source):
 code,job=call('/submissions',{'sourceCode':source});assert code==202,(code,job)
 for _ in range(400):
  code,job=call('/submissions/'+job['submissionId']);assert code==200
  if job['state'] in ['FINISHED','FAILED']:return job
  time.sleep(.3)
 raise AssertionError('Timeout polling')
correct='int countVowels(const string& s){int n=0;for(char c:s)if(string("aeiou").find(c)!=string::npos)n++;return n;}'
code,task=call('/task')
check('own task, one public example, C++ policy',code==200 and task['id']=='team-vowels' and task['allowedLanguages']==['CPP'] and len(task['publicTestCases'])==1)
code,r=call('/run',{'sourceCode':correct,'stdin':'hello\n'})
check('Run Accepted with stdout 2',code==200 and r['status']=='ACCEPTED' and r['stdout']=='2\n')
job=submit(correct);r=job['result']
check('Submit Accepted 4/4',job['state']=='FINISHED' and r['status']=='ACCEPTED' and r['passedTests']==4)
check('benchmark and metrics',r['benchmark']['status']=='COMPLETED' and r['benchmark']['measuredRuns']==5 and r['peakMemoryBytes']>0)
check('public expected and actual results',r['testResults'][0]['expectedOutput']=='2\n' and r['testResults'][0]['output']=='2\n')
check('hidden results redacted',all(t['visibility']=='HIDDEN' and t['expectedOutput'] is None and t['output'] is None and t['stderr'] is None for t in r['testResults'][1:]))
job=submit('int countVowels(const string& s){return 0;}');r=job['result']
check('Wrong Answer 2/4, no benchmark',r['status']=='WRONG_ANSWER' and r['passedTests']==2 and r['benchmark'] is None)
job=submit('int countVowels( {');r=job['result']
check('Compilation Error with diagnostics',r['status']=='COMPILATION_ERROR' and bool(r['compileOutput']))
job=submit('int countVowels(const string& s){throw 1;}');r=job['result']
check('Runtime Error',r['status']=='RUNTIME_ERROR')
job=submit('int countVowels(const string& s){while(true){}return 0;}');r=job['result']
check('TLE and interrupted index',r['status']=='TIME_LIMIT_EXCEEDED' and 'test 1;' in r['message'])
check('empty source rejected',call('/submissions',{'sourceCode':''})[0]==400)
check('invalid function input forwarded as 400',call('/run',{'sourceCode':correct,'stdin':'123'})[0]==400)
check('unknown local job rejected',call('/submissions/unknown')[0]==404)
Path('/tmp/codelobby-team-backend-report.json').write_text(json.dumps({'passed':len(checks),'checks':checks},indent=2))
print(f'{len(checks)}/{len(checks)} checks passed',flush=True)
