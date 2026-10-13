import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const cwd=fileURLToPath(new URL('.',import.meta.url));
for(const [cmd,args] of [[process.execPath,['diagnostic-data.mjs']],[process.env.PYTHON||'python',['rhythm-audit.py']]]){const p=spawnSync(cmd,args,{cwd,stdio:'inherit'});if(p.error)throw p.error;if(p.status!==0)process.exit(p.status??1);}
