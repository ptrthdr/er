"""Real local sandbox checks for the asynchronous evaluation API."""
import os,json,time,urllib.request,urllib.error,copy,uuid
base=os.environ.get('CODELOBBY_API_URL','http://127.0.0.1:8081')
key=os.environ['CODELOBBY_INTERNAL_API_KEY']
checks=0

def call(path,body=None,auth=True):
    headers={'Content-Type':'application/json'}
    if auth: headers['X-CodeLobby-Internal-Key']=key
    request=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
    try:
        with urllib.request.urlopen(request,timeout=10) as r:return r.status,json.load(r)
    except urllib.error.HTTPError as e:return e.code,e.read().decode()
def check(name,ok):
    global checks
    assert ok,name
    checks+=1
    print('PASS',name,flush=True)
def wait(job):
    deadline=time.monotonic()+180
    seen=set()
    while time.monotonic()<deadline:
        code,state=call('/internal/v1/evaluation-jobs/'+job['submissionId'])
        assert code==200
        seen.add(state['state'])
        if state['state'] in ['FINISHED','FAILED']:return state,seen
        time.sleep(.3)
    raise AssertionError('Polling timeout')
prefix=uuid.uuid4().hex
payload={'submissionId':prefix,'language':'CPP','sourceCode':'int countVowels(const string& s){return 0;}','mode':'FUNCTION','adapterId':'vowel-count-v1','limits':{'cpuTimeMs':2000,'wallTimeMs':5000,'memoryLimitBytes':128000000},'testCases':[{'input':'\n','expectedOutput':'0\n','visibility':'HIDDEN'}]}
check('authentication',call('/internal/v1/evaluation-jobs/'+prefix,auth=False)[0]==401)
check('unknown id',call('/internal/v1/evaluation-jobs/missing')[0]==404)
start=time.monotonic();code,job=call('/internal/v1/evaluation-jobs',payload)
check('202 fast acceptance',code==202 and time.monotonic()-start<2)
code,repeat=call('/internal/v1/evaluation-jobs',payload)
check('deduplication',code==202 and repeat['createdAt']==job['createdAt'])
changed={**payload,'sourceCode':'int countVowels(const string& s){return 1;}'}
check('conflicting id',call('/internal/v1/evaluation-jobs',changed)[0]==409)
result,seen=wait(job)
check('finished accepted benchmark',result['state']=='FINISHED' and result['result']['status']=='ACCEPTED' and result['result']['benchmark']['status']=='COMPLETED')
check('hidden result redacted',result['result']['testResults'][0]['output'] is None)
check('timestamps',result['startedAt'] and result['finishedAt'])
invalid={**payload,'submissionId':prefix+'-invalid','adapterId':'unknown'}
code,_=call('/internal/v1/evaluation-jobs',invalid)
check('semantic error rejected before admission',code==400)
wrong={**payload,'submissionId':prefix+'-wrong','testCases':[{'input':'a\n','expectedOutput':'1\n','visibility':'PUBLIC'}]}
_,job=call('/internal/v1/evaluation-jobs',wrong);result,_=wait(job)
check('wrong answer FINISHED',result['state']=='FINISHED' and result['result']['status']=='WRONG_ANSWER')
# Keep the first execution busy while filling all ten pending slots.
slow={**payload,'submissionId':prefix+'-slow','sourceCode':'int countVowels(const string& s){volatile long n=0;while(true){n++;}return 0;}'}
_,job=call('/internal/v1/evaluation-jobs',slow)
accepted=[];rejected=False
for i in range(12):
    p={**wrong,'submissionId':prefix+'-queue-'+str(i)}
    code,data=call('/internal/v1/evaluation-jobs',p)
    if code==202:accepted.append(data)
    elif code==429:rejected=True
check('bounded queue returns 429',rejected)
result,seen=wait(job)
check('running and timeout verdict', 'RUNNING' in seen and result['result']['status']=='TIME_LIMIT_EXCEEDED')
for item in accepted:wait(item)
print(str(checks)+'/'+str(checks)+' checks passed',flush=True)
