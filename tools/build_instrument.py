"""Build the offline instrument and self-contained SDK exports."""
from pathlib import Path
import html,json,re
ROOT=Path(__file__).resolve().parent.parent
DATA=ROOT/'research';ART=ROOT/'instrument'
def js_body(path):
 s=path.read_text(encoding='utf-8');s=re.sub(r'^import .*?;\s*','',s,flags=re.M);s=re.sub(r'^export \{[^\n]+\}(?: from [^\n]+)?;\s*','',s,flags=re.M);return re.sub(r'^export ', '',s,flags=re.M)
names=['polynomialGuide','taylorGuide','sampledGuide','captureGuide','compileGuide','evaluateGuide']
g='const {'+','.join(names)+'}=(()=>{\n'+js_body(ROOT/'vixen-guide.mjs')+'\nreturn {'+','.join(names)+'};})();\n'
names2=['withInfluence','restoreInfluence','INFLUENCE_ALGORITHM']
g+='const {'+','.join(names2)+'}=(()=>{\n'+js_body(ROOT/'vixen-influence.mjs')+'\nreturn {'+','.join(names2)+'};})();\n'
raw=(ROOT/'vixen.mjs').read_text(encoding='utf-8');raw=re.sub(r'^import .*?;\s*','',raw,flags=re.M);raw=re.sub(r"^export \{[^\n]+\} from [^\n]+;\s*",'',raw,flags=re.M)
sdk=g+raw+'\nexport {'+','.join(names)+'};\n';kernel=re.sub(r'^export \{[^\n]+\};\s*','',sdk,flags=re.M);kernel=re.sub(r'^export ','',kernel,flags=re.M)
def py_body(path):
 s=path.read_text(encoding='utf-8');return re.sub(r'^from vixen_(?:guide|influence) import .*\n','',s,flags=re.M)
def py_factory(name,body,exports):return 'def '+name+'():\n'+''.join(' '+line+'\n' for line in body.splitlines())+' return '+repr(tuple(exports)).replace("'",'')+'\n'+','.join(exports)+'='+name+'()\n'
pnames=['polynomial_guide','taylor_guide','sampled_guide','capture_guide','compile_guide','evaluate_guide']
py=py_factory('_guide_sdk_factory',py_body(ROOT/'vixen_guide.py'),pnames)
py+=py_factory('_influence_sdk_factory',py_body(ROOT/'vixen_influence.py'),['with_influence','restore_influence','INFLUENCE_ALGORITHM'])
py+=py_body(ROOT/'vixen.py')
report=json.loads((DATA/'verification-game.json').read_text());assert report['algorithm']=='vixen-hyperfield-c2/4';report['browser_ui']=json.loads((DATA/'ui-verification.json').read_text())
source=(ART/'template.html').read_text(encoding='utf-8')
parts={'__KERNEL__':kernel,'__UI__':(ART/'ui.js').read_text(encoding='utf-8'),'__SDK__':html.escape(sdk),'__PYTHON__':html.escape(py),'__VERIFICATION__':html.escape(json.dumps(report,indent=2)),'__EVIDENCE__':html.escape((DATA/'evidence.json').read_text()),'__RHYTHM__':html.escape((DATA/'rhythm-audit.json').read_text())}
for token,contents in parts.items():source=source.replace(token,contents)
assert not re.search(r'__(?:KERNEL|UI|SDK|PYTHON|VERIFICATION|EVIDENCE|RHYTHM|PLOT)__',source);assert '[truncated]' not in source
(ART/'vixen.html').write_text(source,encoding='utf-8');(ROOT/'.smoke').mkdir(exist_ok=True);(ROOT/'.smoke'/'standalone-vixen.mjs').write_text(sdk,encoding='utf-8');(ROOT/'.smoke'/'standalone_vixen.py').write_text(py,encoding='utf-8');print('Built offline instrument with self-contained SDK exports;',len(source.encode()),'bytes')
