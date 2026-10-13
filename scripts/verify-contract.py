from runner_contract import request_v1
"""Validate OpenAPI and live API samples. Install scripts/contract-requirements.txt first."""
import copy,json,os,time,uuid,urllib.request,urllib.error,concurrent.futures
from pathlib import Path
from openapi_spec_validator import validate
from jsonschema import Draft202012Validator
root=Path(__file__).resolve().parents[1]
spec=json.loads((root/'docs/openapi.json').read_text());validate(spec)
print('PASS OpenAPI 3.1 structure',flush=True)
def schema(name,value):
    Draft202012Validator({'$ref':'#/components/schemas/'+name,'components':spec['components']}).validate(value)
example=spec['paths']['/internal/v1/evaluation-jobs']['post']['requestBody']['content']['application/json']['example']
schema('EvaluationRequest',example);print('PASS documented request example',flush=True)
base=os.environ.get('CODELOBBY_API_URL','http://127.0.0.1:8081');key=os.environ['CODELOBBY_INTERNAL_API_KEY']
def call(path,body=None,auth=True):
    h={'Content-Type':'application/json'}
    if auth:h['X-CodeLobby-Internal-Key']=key
    req=urllib.request.Request(base+path,data=None if body is None else json.dumps(request_v1(body)).encode(),headers=h)
    try:
        with urllib.request.urlopen(req,timeout=120) as r:return r.status,json.load(r)
    except urllib.error.HTTPError as e:return e.code,json.load(e)
code,caps=call('/internal/v1/capabilities');assert code==200;schema('Capabilities',caps)
print('PASS capabilities schema',flush=True)
code,error=call('/internal/v1/capabilities',auth=False);assert code==401;schema('Error',error)
print('PASS authentication schema',flush=True)
p=copy.deepcopy(example);p['submissionId']='contract-'+uuid.uuid4().hex
for name,change in [('adapter',{'adapterId':'unknown'}),('limits',{'limits':{'cpuTimeMs':99999,'wallTimeMs':99999,'memoryLimitBytes':128000000}}),('input',{'testCases':[{'input':'123','expectedOutput':'0','visibility':'HIDDEN'}]}),('output',{'testCases':[{'input':'a','expectedOutput':'x','visibility':'HIDDEN'}]})]:
    validation=json.loads((root/'examples/integration/vowel-evaluation.json').read_text());body={**validation,**change,'submissionId':p['submissionId']};code,error=call('/internal/v1/evaluation-jobs',body)
    assert code==400,(name,code,error);schema('Error',error)
    assert call('/internal/v1/evaluation-jobs/'+p['submissionId'])[0]==404
    print('PASS pre-admission validation '+name,flush=True)
# An accepted evaluation must stay queued while another operation owns the sandbox.
run={'requestId':'slow-run','language':'CPP','mode':'FULL_PROGRAM','sourceCode':'#include <thread>\n#include <chrono>\n#include <iostream>\nint main(){std::this_thread::sleep_for(std::chrono::seconds(3));std::cout<<1;}','stdin':'','limits':{'cpuTimeMs':2000,'wallTimeMs':5000,'memoryLimitBytes':128000000}}
with concurrent.futures.ThreadPoolExecutor() as pool:
    future=pool.submit(call,'/internal/v1/runs',run)
    time.sleep(.5)
    code,job=call('/internal/v1/evaluation-jobs',p);assert code==202;schema('Job',job)
    time.sleep(2.3)
    code,job=call('/internal/v1/evaluation-jobs/'+p['submissionId'])
    assert job['state']=='QUEUED' and job['startedAt'] is None,job
    print('PASS accepted job remains QUEUED beyond 2 seconds',flush=True)
    code,run_result=future.result();assert code==200;schema('RunEnvelope',run_result)
for _ in range(400):
    code,job=call('/internal/v1/evaluation-jobs/'+p['submissionId']);schema('Job',job)
    if job['state'] in ['FINISHED','FAILED']:break
    time.sleep(.3)
assert job['state']=='FINISHED' and job['result']['status']=='ACCEPTED',job
schema('SubmissionResult',job['result']);print('PASS finished job/result/benchmark/test schemas',flush=True)
code,replay=call('/internal/v1/evaluation-jobs',p);assert code==200 and replay==job
print('PASS completed job replay',flush=True)
code,error=call('/internal/v1/evaluation-jobs',{**p,'sourceFiles':[{**p['sourceFiles'][0],'content':p['sourceFiles'][0]['content']+'\n// changed'}]})
assert code==409;schema('Error',error);assert not error['retryable']
print('PASS conflict error schema',flush=True)
print('ALL CHECKS PASSED',flush=True)
