"""Offline contract checks. No running API, secrets, or sandbox required."""
import json
from pathlib import Path
from openapi_spec_validator import validate
from jsonschema import Draft202012Validator
root = Path(__file__).resolve().parents[1]
spec = json.loads((root / 'docs/openapi.json').read_text())
validate(spec)
for path in spec['paths'].values():
    for operation in path.values():
        if not isinstance(operation, dict):
            continue
        body = operation.get('requestBody', {}).get('content', {}).get('application/json', {})
        if 'example' in body:
            schema = dict(body['schema'], components=spec['components'])
            Draft202012Validator(schema).validate(body['example'])
print('PASS OpenAPI and documented request examples (offline)')
