import fs from 'node:fs';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
import {chromium} from 'playwright-core';
import {ALGORITHM,create} from '../vixen.mjs';

const executablePath=process.env.BROWSER_EXE||['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe','C:/Program Files/Google/Chrome/Application/chrome.exe','/usr/bin/google-chrome','/usr/bin/chromium'].find(p=>fs.existsSync(p));
assert.ok(executablePath,'Set BROWSER_EXE to an installed Chromium-family browser');
const browser=await chromium.launch({headless:true,executablePath});
const results=[];
try{
 for(const width of [1200,390,320]){
  const page=await browser.newPage({viewport:{width,height:900}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(pathToFileURL(path.resolve('instrument/vixen.html')).href);await page.waitForFunction(()=>window.vixenInstrument);
  for(const floor of ['none','zero','baseline','backbone']){
   const cfg={seed:'ui-constraints',floor,boundarySoftness:.73123,ceilingMargin:.13257,stabilizer:.73};
   await page.locator('#config').fill(JSON.stringify({algorithm:ALGORITHM,config:cfg}));await page.locator('#applyConfig').click();
   await page.waitForFunction(c=>{const r=window.vixenInstrument.config;return r.seed===c.seed&&r.floor===c.floor&&r.ceilingMargin===c.ceilingMargin&&r.boundarySoftness===c.boundarySoftness;},cfg);
   assert.equal(await page.locator('#error').textContent(),'');
   const data=await page.evaluate(()=>({config:window.vixenInstrument.config,rows:[100,144.25,233.125,300].map(x=>window.vixenInstrument.sample(x)),overflow:document.documentElement.scrollWidth>innerWidth+1}));
   assert.equal(data.overflow,false);assert.equal(data.config.ceilingMargin,cfg.ceilingMargin);assert.equal(data.config.boundarySoftness,cfg.boundarySoftness);
   for(const row of data.rows){const expected=create(cfg).sample(row.x);for(const key of Object.keys(expected)){if(typeof expected[key]==='number')assert.ok(Math.abs(row[key]-expected[key])/Math.max(1,Math.abs(row[key]),Math.abs(expected[key]))<1e-10,`Cross-libm mismatch: ${key}`);else assert.equal(row[key],expected[key]);}}
  }
  await page.locator('#ceilingEnabled').uncheck();await page.waitForFunction(()=>window.vixenInstrument.config.ceilingMargin===null);
  await page.locator('#floor').selectOption('zero');await page.waitForFunction(()=>window.vixenInstrument.config.floor==='zero');
  const nonnegative=await page.evaluate(()=>Array.from({length:600},(_,i)=>window.vixenInstrument.sample(i+1).value).every(v=>v>=0));assert.equal(nonnegative,true);
  await page.locator('#csv').click();const csv=await page.locator('#csvText').inputValue();const lines=csv.split('\n'),keys=lines[0].split(','),valueIndex=keys.indexOf('value');assert.ok(keys.includes('unconstrainedValue')&&keys.includes('floorValue'));assert.ok(lines.slice(1).every(l=>Number(l.split(',')[valueIndex])>=0));
  assert.deepEqual(errors,[]);results.push({width,floors:4,preciseConfig:true,nonnegativeCSV:true,columns:keys.length,overflow:false,pageErrors:errors});await page.close();
 }
 console.log(JSON.stringify({viewports:results},null,2));fs.writeFileSync('research/ui-verification.json',JSON.stringify({standalone:true,viewports:results},null,2));
}finally{await browser.close();}
