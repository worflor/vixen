"""Build Vixen using the same tested source shipped to games."""
from pathlib import Path
import html,json,re
ROOT=Path(__file__).resolve().parent.parent
DATA=ROOT/"research"
ART=ROOT/"instrument"
sdk=(ROOT/'vixen.mjs').read_text(encoding='utf-8');kernel=re.sub(r'^export ', '',sdk,flags=re.M)
report=json.loads((DATA/'verification-game.json').read_text());assert report['algorithm']=='vixen-hyperfield-c2/2'
report['browser_ui']=json.loads((DATA/'ui-verification.json').read_text())
source=(ART/'template.html').read_text(encoding='utf-8')
parts={'__KERNEL__':kernel,'__UI__':(ART/'ui.js').read_text(encoding='utf-8'),'__SDK__':html.escape(sdk),'__PYTHON__':html.escape((ROOT/'vixen.py').read_text(encoding='utf-8')),'__VERIFICATION__':html.escape(json.dumps(report,indent=2)),'__EVIDENCE__':html.escape((DATA/'evidence.json').read_text()),'__RHYTHM__':html.escape((DATA/'rhythm-audit.json').read_text())}
for token,contents in parts.items():source=source.replace(token,contents)
assert not re.search(r'__(?:KERNEL|UI|SDK|PYTHON|VERIFICATION|EVIDENCE|RHYTHM|PLOT)__',source)
assert '[truncated]' not in source
(ART/'vixen.html').write_text(source,encoding='utf-8');print('Built Vixen;',len(source.encode()),'bytes; tested kernel embedded')
