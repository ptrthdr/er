"""Create local configuration without overwriting existing files or printing secrets."""
from pathlib import Path
import re,secrets
root=Path(__file__).resolve().parents[1]
config=root/'infra/judge0.conf'
if not config.exists():
    template=(root/'infra/judge0.conf.example').read_text()
    for key in ['REDIS_PASSWORD','POSTGRES_PASSWORD']:
        template=re.sub(r'^'+key+r'=.*$',key+'='+secrets.token_hex(24),template,flags=re.M)
    config.write_text(template)
    try:config.chmod(0o600)
    except OSError:pass
    print('Created infra/judge0.conf')
else:print('Preserved existing infra/judge0.conf')
env=root/'.env.local'
if not env.exists():
    env.write_text('CODELOBBY_INTERNAL_API_KEY='+secrets.token_hex(32)+'\n')
    try:env.chmod(0o600)
    except OSError:pass
    print('Created .env.local (server secret; do not share)')
else:print('Preserved existing .env.local')
