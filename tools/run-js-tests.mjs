import {readdirSync} from 'node:fs';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {resolve} from 'node:path';
import {spawnSync} from 'node:child_process';

const directory=new URL('../tests/',import.meta.url);
export function discoverTests(){
 return readdirSync(directory).filter(name=>/^test-.*\.mjs$/.test(name)).sort()
  .map(name=>fileURLToPath(new URL(name,directory)));
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
 const result=spawnSync(process.execPath,['--test',...discoverTests()],{stdio:'inherit'});
 if(result.error)console.error(result.error.message);
 process.exitCode=result.status??1;
}
