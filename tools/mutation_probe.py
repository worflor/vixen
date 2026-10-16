"""Run discriminating mutants only in disposable project copies."""
from pathlib import Path
import subprocess,shutil,tempfile,json
ROOT=Path(__file__).resolve().parents[1]
source=(ROOT/'vixen.mjs').read_text(encoding='utf-8')
mutants=[
 ('gate','10+t*(-15+6*t)','9+t*(-15+6*t)'),
 ('history','if(!last||x>=last.entry.at+last.entry.handoffWidth)return {base:leaf,active:[]};','if(true)return {base:leaf,active:[]};'),
 ('adapter','r.activity=.5+.5*Math.tanh(r.deviation)','r.activity=.4+.5*Math.tanh(r.deviation)'),
 ('zero-floor',"c.floor==='zero'?0", "c.floor==='zero'?-1"),
 ('snapshot-envelope',"if(Reflect.ownKeys(snapshot).some(k=>typeof k!=='string'||!['algorithm','config','lineage'].includes(k)))throw new TypeError('Unknown snapshot field');",'')]
files=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard'],cwd=ROOT,text=True).splitlines()
tests=sorted(p for p in files if p.startswith('tests/test-') and p.endswith('.mjs'))
reports=[]
parent=ROOT/'.smoke';parent.mkdir(exist_ok=True)
for name,old,new in mutants:
 assert source.count(old)==1,(name,source.count(old))
 with tempfile.TemporaryDirectory(prefix='mutant-',dir=parent) as temporary:
  target=Path(temporary)
  for relative in files:
   origin=ROOT/relative
   if origin.is_file():
    out=target/relative;out.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(origin,out)
  (target/'vixen.mjs').write_text(source.replace(old,new),encoding='utf-8')
  result=subprocess.run(['node','--test',*tests],cwd=target,text=True,encoding='utf-8',capture_output=True)
  text=result.stdout+result.stderr
  assert 'SyntaxError' not in text,(name,text)
  killed=result.returncode!=0
  reports.append({'mutant':name,'killed':killed,'exit_code':result.returncode})
report={'scope':'five selected runtime mutations, not exhaustive mutation coverage','mutants':reports,'killed':sum(r['killed'] for r in reports)}
print(json.dumps(report,indent=2))
(ROOT/'research/mutation-probes.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
assert all(r['killed'] for r in reports)
