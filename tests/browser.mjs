import fs from 'node:fs';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
import {chromium} from 'playwright-core';
import {ALGORITHM,create,restore} from '../vixen.mjs';

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
  const timelineConfig={seed:'ui-timeline',openingEndpoint:160,introVariation:1,floor:'zero',stabilizer:.3};
  await page.locator('#config').fill(JSON.stringify({algorithm:ALGORITHM,config:timelineConfig}));await page.locator('#applyConfig').click();
  await page.waitForFunction(()=>window.vixenInstrument.config.openingEndpoint===160&&window.vixenInstrument.config.introVariation===1);
  const rootHistory=await page.evaluate(()=>[1,42,160,199,200].map(x=>window.vixenInstrument.sample(x)));
  await page.locator('#inspectX').fill('200');await page.locator('#operation').fill('opened-left-door');await page.locator('#handoffWidth').fill('24');await page.locator('#forkTimeline').click();
  await page.waitForFunction(()=>window.vixenInstrument.lineage.length===1);
  assert.deepEqual(await page.evaluate(()=>[1,42,160,199,200].map(x=>window.vixenInstrument.sample(x))),rootHistory);
  assert.equal(await page.locator('#seed').isDisabled(),true);assert.equal(await page.locator('#openingEndpoint').isDisabled(),true);
  assert.equal(await page.locator('#comparisonLegend').textContent(),'Dashed violet: parent timeline');
  const parentHistory=await page.evaluate(()=>[201,210,220,239,240].map(x=>window.vixenInstrument.sample(x)));
  await page.locator('#inspectX').fill('240');await page.locator('#operation').fill('took-key');await page.locator('#forkTimeline').click();await page.waitForFunction(()=>window.vixenInstrument.lineage.length===2);
  assert.deepEqual(await page.evaluate(()=>[201,210,220,239,240].map(x=>window.vixenInstrument.sample(x))),parentHistory);
  await page.locator('summary').filter({hasText:'Shape and display controls'}).click();await page.locator('#output').selectOption('activity');await page.waitForFunction(()=>document.getElementById('axes').textContent.toLowerCase().includes('activity'));assert.equal(await page.evaluate(()=>window.vixenInstrument.lineage.length),2);
  await page.locator('#viewSnapshot').click();const snapshot=JSON.parse(await page.locator('#config').inputValue());assert.equal(snapshot.lineage.length,2);assert.equal(snapshot.config.seed,'ui-timeline');
  const expectedTimeline=restore(snapshot),branchRows=await page.evaluate(()=>[1,200,212,240,252,280,300].map(x=>window.vixenInstrument.sample(x)));
  for(const row of branchRows)for(const [key,value] of Object.entries(expectedTimeline.sample(row.x))){if(typeof value==='number')assert.ok(Math.abs(row[key]-value)/Math.max(1,Math.abs(row[key]),Math.abs(value))<1e-10);else assert.equal(row[key],value);}
  await page.locator('#csv').click();const timelineCSV=(await page.locator('#csvText').inputValue()).split('\n'),header=timelineCSV[0].split(',');for(const x of [200,212,240,252,280]){const values=timelineCSV[x].split(',');assert.ok(Math.abs(Number(values[header.indexOf('value')])-expectedTimeline.sample(x).value)<1e-7);}
  await page.locator('#newRoot').click();assert.equal(await page.locator('#seed').isDisabled(),false);await page.locator('#config').fill(JSON.stringify(snapshot));await page.locator('#applyConfig').click();await page.waitForFunction(()=>window.vixenInstrument.lineage.length===2);assert.deepEqual(await page.evaluate(()=>window.vixenInstrument.snapshot()),snapshot);
  await page.locator('#reset').click();await page.locator('#config').fill(JSON.stringify({algorithm:ALGORITHM,config:{openingEndpoint:2,introVariation:1}}));await page.locator('#applyConfig').click();await page.waitForFunction(()=>window.vixenInstrument.config.openingEndpoint===2);
  assert.equal(await page.evaluate(()=>Number(document.getElementById('extent').value)<=window.vixenInstrument.domain[1]),true);assert.equal(await page.evaluate(()=>window.vixenSpectrum.available),false);assert.equal(await page.locator('#error').textContent(),'');
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);
  assert.deepEqual(errors,[]);results.push({width,floors:4,preciseConfig:true,nonnegativeCSV:true,timing:true,seededIntro:true,nestedForkHistory:true,lineageReplay:true,branchCSV:true,rootLocked:true,shortDomain:true,columns:keys.length,overflow:false,pageErrors:errors});await page.close();
 }
 console.log(JSON.stringify({viewports:results},null,2));fs.writeFileSync('research/ui-verification.json',JSON.stringify({standalone:true,viewports:results},null,2));
}finally{await browser.close();}
