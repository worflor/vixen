import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import {pathToFileURL} from 'node:url';
import {chromium} from 'playwright-core';
import {createCoherent,createExtended,couple} from '../vixen.mjs';
function same(actual,expected){
 if(typeof expected==='number'){assert.ok(typeof actual==='number'&&Math.abs(actual-expected)<=1e-10*Math.max(1,Math.abs(actual),Math.abs(expected)),`${actual} differs from ${expected}`);return;}
 if(expected&&typeof expected==='object'){assert.deepEqual(Object.keys(actual).sort(),Object.keys(expected).sort());for(const k of Object.keys(expected))same(actual[k],expected[k]);return;}
 assert.deepEqual(actual,expected);
}
const executablePath=process.env.BROWSER_EXE||['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe','C:/Program Files/Google/Chrome/Application/chrome.exe'].find(p=>fs.existsSync(p));
assert.ok(executablePath);
const file=path.resolve('instrument/vixen.html');
const server=http.createServer((req,res)=>{res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});fs.createReadStream(file).pipe(res);});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({headless:true,executablePath});
try{
 let deep=createCoherent({seed:'deep-browser'});for(let i=0;i<40;i++)deep=deep.impulse(120+i,.01,20);
 const group=couple([createCoherent(),createCoherent({seed:2})],.4,[120,200]);
 const extended=createExtended({seed:'wide-browser'});
 const cases=[{snapshot:deep.snapshot(),x:180,expected:deep.sampleNormalized(180)},{snapshot:group.curves[0].snapshot(),x:160,expected:group.curves[0].sampleNormalized(160)},{snapshot:extended.snapshot(),x:180,expected:extended.sampleNormalized(180)},{snapshot:extended.tail(1e9),x:1e9+25,expected:extended.sampleNormalized(1e9+25)}];
 for(const url of [pathToFileURL(file).href,`http://127.0.0.1:${server.address().port}/vixen.html`]){
  const page=await browser.newPage({viewport:{width:390,height:900}});const errors=[];page.on('pageerror',e=>errors.push(e.message));await page.goto(url);await page.waitForFunction(()=>window.vixenInstrument);
  for(const row of cases){
   await page.locator('#config').fill(JSON.stringify(row.snapshot));await page.locator('#applyConfig').click();
   assert.equal(await page.locator('#error').textContent(),'');
   const actual=await page.evaluate(x=>window.vixenInstrument.sampleNormalized(x),row.x);same(actual,row.expected);
   await page.locator('#inspectX').fill(String(row.x));await page.locator('#inspectX').dispatchEvent('input');
   assert.equal(await page.locator('#error').textContent(),'');
   assert.ok(await page.locator('#seed').isDisabled(),'Loaded composition must not silently reset root semantics');
   assert.equal(await page.evaluate(()=>Number(document.querySelector('#extent').value)-window.vixenInstrument.domain[0]<=50000),true);
  }
  assert.deepEqual(errors,[]);await page.close();
 }
 console.log('Browser file and HTTP: deep/shared graph import, extended mode, far checkpoint window and normalized inspection passed');
}finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
