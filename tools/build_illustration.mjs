import fs from 'node:fs';
import {fileURLToPath} from 'node:url';
import {create, ALGORITHM} from '../vixen.mjs';

const width = 1000, height = 440;
const left = 78, top = 80, right = 28, bottom = 54;
const w = width - left - right, h = height - top - bottom;
const levels = [0, 0.5, 1];
const colors = ['#f2a36b', '#b6a2ff', '#72d7c4'];
const series = levels.map(stabilizer => {
  const curve = create({seed: 'vixen', stabilizer});
  return Array.from({length: 1197}, (_, i) => curve.sample(1 + i / 4));
});
const values = series.flatMap(rows => rows.map(row => row.value));
const min = Math.min(0, ...values), max = Math.max(...values);
const pad = (max - min) * 0.06, lo = min - pad, hi = max + pad;
const px = x => left + (x - 1) / 299 * w;
const py = y => top + (hi - y) / (hi - lo) * h;
const paths = series.map((rows, i) => `<path d="${rows.map((r,j)=>`${j?'L':'M'}${px(r.x).toFixed(2)},${py(r.value).toFixed(2)}`).join(' ')}" fill="none" stroke="${colors[i]}" stroke-width="2"/>`).join('\n');
const grid = Array.from({length: 5}, (_, i) => {
  const y = lo + (hi - lo) * i / 4;
  return `<line x1="${left}" y1="${py(y)}" x2="${width-right}" y2="${py(y)}" stroke="#293140"/><text x="${left-10}" y="${py(y)+4}" text-anchor="end" fill="#9ba7b8">${Math.round(y).toLocaleString('en-US')}</text>`;
}).join('\n');
const ticks = [1,100,200,300].map(x=>`<text x="${px(x)}" y="${height-28}" text-anchor="middle" fill="#9ba7b8">${x}</text>`).join('');
const legend = levels.map((s,i)=>`<circle cx="${555+i*130}" cy="35" r="4" fill="${colors[i]}"/><text x="${566+i*130}" y="40" fill="${colors[i]}">stabilizer ${s}</text>`).join('');
const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" role="img" aria-labelledby="title desc"><title id="title">Vixen: same seed, different stabilization</title><desc id="desc">Actual sample.value from ${ALGORITHM}, seed vixen, default origin and velocity, x 1–300 sampled every 0.25 units. Orange: stabilizer zero. Purple: 0.5. Teal: one.</desc><rect width="100%" height="100%" rx="12" fill="#0d121b"/><g font-family="system-ui,sans-serif" font-size="12"><text x="28" y="40" fill="#edf1f7" font-size="19" font-weight="600">One seed. Three degrees of coherence.</text>${legend}<rect x="${left}" y="${top}" width="${px(100)-left}" height="${h}" fill="#ffffff" opacity="0.035"/>${grid}<line x1="${px(100)}" y1="${top}" x2="${px(100)}" y2="${top+h}" stroke="#67758a" stroke-dasharray="4 5"/>${paths}${ticks}<text x="${left+8}" y="${top+17}" fill="#9ba7b8">authored opening</text><text x="${width-right}" y="${height-8}" text-anchor="end" fill="#9ba7b8">progression x · raw value · seed: vixen</text></g></svg>`;
const out = fileURLToPath(new URL('../docs/media/stabilization.svg', import.meta.url));
fs.mkdirSync(fileURLToPath(new URL('../docs/media/', import.meta.url)), {recursive:true});
fs.writeFileSync(out, svg);
console.log(`Generated illustration from ${series.reduce((n,s)=>n+s.length,0)} actual samples`);
