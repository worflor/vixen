import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import {pathToFileURL} from 'node:url';
import {chromium} from 'playwright-core';
import {createCoherent} from '../vixen.mjs';
const executablePath=process.env.BROWSER_EXE||['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe','C:/Program Files/Google/Chrome/Application/chrome.exe'].find(p=>fs.existsSync(p));
assert.ok(executablePath,'A Chromium-family browser is required');
const file=path.resolve('instrument/vixen.html');
const server=http.createServer((req,res)=>{if(req.url!=='/vixen.html'){res.writeHead(404);res.end();return;}res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});fs.createReadStream(file).pipe(res);});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({headless:true,executablePath});
try{
 for(const url of [pathToFileURL(file).href,`http://127.0.0.1:${server.address().port}/vixen.html`]){
  const page=await browser.newPage({viewport:{width:390,height:900}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(url);await page.waitForFunction(()=>window.vixenInstrument);
  const root=createCoherent({seed:'coherent-import',floor:'baseline',ceilingMargin:.3});
  await page.locator('#config').fill(JSON.stringify(root.snapshot()));await page.locator('#applyConfig').click();
  await page.waitForFunction(()=>window.vixenInstrument.config.seed==='coherent-import');
  assert.equal(await page.evaluate(()=>window.vixenInstrument.snapshot().algorithm),root.snapshot().algorithm,'Unbranched coherent import must not silently become legacy');
  const composed=root.impulse(120,.1,20).fork({at:160,operation:'browser-replay',handoffWidth:12});
  await page.locator('#config').fill(JSON.stringify(composed.snapshot()));await page.locator('#applyConfig').click();
  await page.waitForTimeout(500);assert.equal(await page.locator('#error').textContent(),'');
  const actual=await page.evaluate(()=>window.vixenInstrument.sample(180));
  for(const [key,value] of Object.entries(composed.sample(180)))if(typeof value==='number')assert.ok(Math.abs(actual[key]-value)<=1e-10*Math.max(1,Math.abs(value)),key);else assert.equal(actual[key],value);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);assert.deepEqual(errors,[]);await page.close();
 }
 console.log('File and loopback HTTP: coherent identity and composed snapshot replay passed');
}finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
