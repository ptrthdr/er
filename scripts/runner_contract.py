"""Construct v1 requests from legacy local test fixtures (not an API compatibility layer)."""
import copy

def request_v1(body):
    body=copy.deepcopy(body)
    body.setdefault('taskVersionId','local-fixture-v1')
    body.setdefault('mode','FULL_PROGRAM')
    if 'sourceCode' in body:
        source=body.pop('sourceCode')
        name={'CPP':'main.cpp','PYTHON':'main.py','JAVA':'Main.java'}[body.get('language','CPP')]
        body['sourceFiles']=[{'path':name,'content':source}]
    return body
