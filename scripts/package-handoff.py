"""Build a local source-only team bundle from an explicit allowlist."""
import hashlib,json,zipfile
from pathlib import Path
from datetime import datetime,timezone
root=Path(__file__).resolve().parents[1]
out=root/'deliverables';out.mkdir(exist_ok=True)
files=[root/'README.md',root/'.gitignore']
for folder in ['backend','frontend','infra','docs','examples','scripts']:
    for path in (root/folder).rglob('*'):
        if not path.is_file() or path.is_symlink():continue
        rel=path.relative_to(root)
        if any(part in {'target','node_modules','dist','.git','.idea','.vscode','__pycache__'} for part in rel.parts):continue
        if path.name=='judge0.conf' or path.name.startswith('.env') or path.suffix in {'.log','.pyc','.class','.jar','.iso'}:continue
        files.append(path)
manifest={'createdAt':datetime.now(timezone.utc).isoformat(),'contents':'Source/config templates/docs only; no Docker images or local secrets','files':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(files)}}
archive=out/'codelobby-execution-team.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(files):z.write(p,'codelobby-execution/'+str(p.relative_to(root)))
    z.writestr('codelobby-execution/HANDOFF-MANIFEST.json',json.dumps(manifest,indent=2))
(out/'codelobby-execution-team.zip.sha256').write_text(hashlib.sha256(archive.read_bytes()).hexdigest()+'  '+archive.name+'\n')
print(str(archive))
print(str(len(files))+' files; Docker image downloaded separately via pinned digest')
