import json,time,urllib.request
from pathlib import Path
import os
base=os.environ.get('CODELOBBY_API_URL','http://127.0.0.1:8081')
naive_py='''def longest_palindrome(s):
    best = 0
    for left in range(len(s)):
        for right in range(left, len(s)):
            a, b = left, right
            palindrome = True
            while a < b:
                if s[a] != s[b]:
                    palindrome = False
                    break
                a += 1
                b -= 1
            if palindrome:
                best = max(best, right-left+1)
    return best
'''
naive_java='''static int longestPalindrome(String s) {
int best=0;
for(int left=0;left<s.length();left++)for(int right=left;right<s.length();right++){
boolean palindrome=true;
for(int a=left,b=right;a<b;a++,b--)if(s.charAt(a)!=s.charAt(b)){palindrome=false;break;}
if(palindrome)best=Math.max(best,right-left+1);
}return best;
}'''
rows=[]
for lang,extension,naive,wrong in [('CPP','cpp',Path('examples/palindrome/naive.cpp').read_text(),'int longestPalindrome(const string& s){return 0;}'),('PYTHON','py',naive_py,'def longest_palindrome(s):\n    return 0\n'),('JAVA','java',naive_java,'static int longestPalindrome(String s){return 0;}')]:
 for kind,source in [('optimal',Path('examples/palindrome/optimal.'+extension).read_text()),('naive',naive),('wrong',wrong)]:
  started=time.monotonic()
  req=urllib.request.Request(base+'/api/submission-jobs',data=json.dumps({'taskId':'longest-palindrome','language':lang,'sourceCode':source}).encode(),headers={'Content-Type':'application/json'})
  with urllib.request.urlopen(req,timeout=10) as f:job=json.load(f)
  accepted_ms=round((time.monotonic()-started)*1000)
  states=[job['state']]
  for _ in range(400):
   with urllib.request.urlopen(base+'/api/submission-jobs/'+job['submissionId'],timeout=10) as f:job=json.load(f)
   if job['state'] not in states:states.append(job['state'])
   if job['state'] in ['FINISHED','FAILED']:break
   time.sleep(.3)
  result=job.get('result') or {}
  row={'language':lang,'solution':kind,'state':job['state'],'status':result.get('status'),'passed':result.get('passedTests'),'total':result.get('totalTests'),'runtimeMs':result.get('runtimeMs'),'peakMemoryBytes':result.get('peakMemoryBytes'),'benchmark':result.get('benchmark'),'acceptanceMs':accepted_ms,'waitingSeconds':round(time.monotonic()-started,2),'states':states,'error':job.get('error')}
  rows.append(row)
  Path(os.environ.get('CODELOBBY_SCENARIO_REPORT','/tmp/codelobby-scenario-results.json')).write_text(json.dumps(rows,indent=2))
  print(json.dumps(row),flush=True)
