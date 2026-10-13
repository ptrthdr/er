"""Flat multi-file v1 E2E. Requires runner, patched Judge0 and server API key."""
import copy,json,os,time,uuid,urllib.request,urllib.error
base=os.environ.get('CODELOBBY_API_URL','http://127.0.0.1:8081').rstrip('/')
key=os.environ['CODELOBBY_INTERNAL_API_KEY']
checks=[]
limits={'cpuTimeMs':2000,'wallTimeMs':5000,'memoryLimitBytes':128000000}
def call(path,body=None):
    req=urllib.request.Request(base+'/internal/v1'+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json','X-CodeLobby-Internal-Key':key})
    try:
        with urllib.request.urlopen(req,timeout=180) as r:return r.status,json.load(r)
    except urllib.error.HTTPError as e:return e.code,json.load(e)
def check(name,condition):
    if not condition:raise AssertionError(name)
    checks.append(name);print('PASS',name,flush=True)
def files(language):
    if language=='JAVA':return [{'path':'Main.java','content':'public class Main{public static void main(String[]a){System.out.println(Calc.twice(new java.util.Scanner(System.in).nextInt()));}}'},{'path':'Calc.java','content':'class Calc{static int twice(int n){return n*2;}}'}]
    if language=='PYTHON':return [{'path':'main.py','content':'from helpers import twice\nprint(twice(int(input())))\n'},{'path':'helpers.py','content':'def twice(n):\n    return n*2\n'}]
    return [{'path':'main.cpp','content':'#include <iostream>\n#include "calc.h"\nint main(){int n;std::cin>>n;std::cout<<twice(n)<<"\\n";}'},{'path':'calc.h','content':'int twice(int);'},{'path':'calc.cpp','content':'#include "calc.h"\nint twice(int n){return n*2;}'}]
def body(language):return {'taskVersionId':'double-v1','language':language,'mode':'FULL_PROGRAM','sourceFiles':files(language),'limits':limits}
def submit(payload):
    payload={**payload,'submissionId':uuid.uuid4().hex}
    code,job=call('/evaluation-jobs',payload);check('new job QUEUED',code==202 and job['state']=='QUEUED')
    deadline=time.monotonic()+180
    while time.monotonic()<deadline:
        code,job=call('/evaluation-jobs/'+payload['submissionId'])
        if job['state'] in ['FINISHED','FAILED']:return payload,job
        time.sleep(.2)
    raise AssertionError('job timeout')
code,caps=call('/capabilities');check('flat capabilities and actual runtime names',code==200 and not caps['files']['supportsDirectories'] and all('read-from' not in v for v in caps['runtimeVersions'].values()))
for language in ['JAVA','PYTHON','CPP']:
    p=body(language)
    code,result=call('/runs',{**p,'requestId':'r-'+uuid.uuid4().hex,'stdin':'5\n'})
    check(language+' multi-file Run',code==200 and result['result']['status']=='ACCEPTED' and result['result']['stdout']=='10\n')
    p['testCases']=[{'input':'5\n','expectedOutput':'10\n','visibility':'PUBLIC'},{'input':'-2\n','expectedOutput':'-4\n','visibility':'HIDDEN'}]
    original,job=submit(p);result=job.get('result') or {}
    check(language+' multi-file Submit + benchmark',job['state']=='FINISHED' and result['status']=='ACCEPTED' and result['benchmark']['status']=='COMPLETED')
    check(language+' hidden fields redacted',all(result['testResults'][1][f] is None for f in ['output','expectedOutput','stderr']))
    code,replay=call('/evaluation-jobs',{**original,'sourceFiles':list(reversed(original['sourceFiles']))})
    check(language+' reordered files replay',code==200 and replay==job)
    code,error=call('/evaluation-jobs',{**original,'taskVersionId':'different-v2'})
    check(language+' version conflict',code==409 and error['code']=='SUBMISSION_ID_CONFLICT')
# A compilation error in an UNUSED Java source must still be detected.
p=body('JAVA');p['sourceFiles'].append({'path':'Unused.java','content':'class Unused { this is invalid }'})
code,res=call('/runs',{**p,'requestId':'unused-java','stdin':'5\n'})
check('all Java files compiled',code==200 and res['result']['status']=='COMPILATION_ERROR')
for path in ['../main.py','src/main.py','/main.py','main\\.py','compile.sh']:
    p=body('PYTHON');p['sourceFiles'][0]['path']=path
    code,error=call('/runs',{**p,'requestId':'invalid','stdin':''})
    check('reject path '+path,code==400)
p=body('PYTHON');p['sourceFiles'].append({'path':'Main.py','content':'pass'})
check('case-insensitive collision',call('/runs',{**p,'requestId':'collision','stdin':''})[0]==400)
# File mutations must not carry into later tests or benchmark rounds.
p=body('PYTHON');p['sourceFiles']=[{'path':'main.py','content':'from pathlib import Path\np=Path("state.txt")\nprint("dirty" if p.exists() else "clean")\np.write_text("student-data")\n'}]
p['testCases']=[{'input':'','expectedOutput':'clean\n','visibility':'PUBLIC'}]*2
_,job=submit(p);check('fresh writable filesystem between tests/benchmark',job['state']=='FINISHED' and job['result']['status']=='ACCEPTED')
for stream in ['stdout','stderr']:
    p=body('PYTHON');p['sourceFiles']=[{'path':'main.py','content':'import sys\nsys.'+stream+'.write("x"*100000)\n'}]
    code,res=call('/runs',{**p,'requestId':'output-'+stream,'stdin':''})
    check(stream+' output limit verdict',code==200 and res['result']['status']=='OUTPUT_LIMIT_EXCEEDED' and len(res['result'][stream].encode())<=65536)
p=body('PYTHON');p['sourceFiles']=[{'path':'main.py','content':'print("x"*100000)'}];p['testCases']=[{'input':'','expectedOutput':'x'*65536,'visibility':'HIDDEN'}]
_,job=submit(p);check('truncated output never Accepted',job['state']=='FINISHED' and job['result']['status']=='OUTPUT_LIMIT_EXCEEDED')
# No usable outbound network in the student's namespace.
p=body('PYTHON');p['sourceFiles']=[{'path':'main.py','content':'import socket\ns=socket.socket();s.settimeout(.2)\ntry:\n    s.connect(("1.1.1.1",80));print("network-enabled")\nexcept OSError:\n    print("blocked")\n'}]
code,res=call('/runs',{**p,'requestId':'network','stdin':''});check('outbound connection blocked',code==200 and res['result']['stdout']=='blocked\n')
# Output caps must not silently lower the separate scratch-file allowance.
p=body('PYTHON');p['sourceFiles']=[{'path':'main.py','content':'from pathlib import Path\np=Path("scratch.dat")\np.write_bytes(b"x"*100000)\nprint(len(p.read_bytes()))\n'}]
code,res=call('/runs',{**p,'requestId':'scratch','stdin':''});check('scratch file >64KiB remains usable',code==200 and res['result']['status']=='ACCEPTED' and res['result']['stdout']=='100000\n')
p=body('PYTHON');p['sourceFiles']=[{'path':'main.py','content':'import os\nfrom pathlib import Path\nprint("safe" if os.getenv("CODELOBBY_INTERNAL_API_KEY") is None and os.getenv("REDIS_PASSWORD") is None and not Path("/judge0.conf").exists() else "leak")\n'}]
code,res=call('/runs',{**p,'requestId':'secrets','stdin':''});check('service keys/config not exposed in sandbox',code==200 and res['result']['stdout']=='safe\n')
# Raw HTTP body is bounded before deserialization for both endpoints.
for endpoint in ['/runs','/evaluation-jobs']:
    req=urllib.request.Request(base+'/internal/v1'+endpoint,data=b' '*2000001,headers={'Content-Type':'application/json','X-CodeLobby-Internal-Key':key})
    try:
        urllib.request.urlopen(req);raise AssertionError('oversized body accepted')
    except urllib.error.HTTPError as error:
        data=json.load(error);check('raw body limit '+endpoint,error.code==413 and data['code']=='PAYLOAD_TOO_LARGE')
# Chunked body without Content-Length gets the same guard.
req=urllib.request.Request(base+'/internal/v1/runs',data=iter([b' '*1000000,b' '*1000001]),headers={'Content-Type':'application/json','X-CodeLobby-Internal-Key':key})
try:
    urllib.request.urlopen(req);raise AssertionError('chunked oversized body accepted')
except urllib.error.HTTPError as error:
    data=json.load(error);check('chunked body limit',error.code==413 and data['code']=='PAYLOAD_TOO_LARGE')
p=body('PYTHON');code,error=call('/runs',{**p,'requestId':'utf8-input','stdin':'ą'*50001})
check('input limit counts UTF8 bytes',code==400)
print(str(len(checks))+'/'+str(len(checks))+' checks passed')
report=os.environ.get('CODELOBBY_VERIFY_REPORT')
if report:
    from pathlib import Path
    Path(report).write_text(json.dumps({'passed':len(checks),'checks':checks},indent=2))
