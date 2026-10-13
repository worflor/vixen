"""Empirical rhythm audit. NumPy is a diagnostic dependency, not an SDK dependency."""
import json
from pathlib import Path
import numpy as np
ROOT=Path(__file__).parent
d=json.loads((ROOT/'diagnostic-data.json').read_text());n=d['n'];dt=d['step'];window=np.hanning(n);t=np.arange(n)*dt;freq=np.fft.rfftfreq(n,dt);report={'scope':'Finite-window numerical diagnostics; not proof of chaos, independence, or no periodicity','hyperfield_reference_config':d['hyperDefaults'],'seeds':len(d['seeds']),'windows':[[s,s+(n-1)*dt] for s in d['starts']],'models':{},'sensitivity':[]}
for name,windows in d['models'].items():
 metrics=[]
 for rows in windows:
  a=np.array(rows);cs=np.corrcoef(a);energy=a*a;energy=(energy-energy.mean(axis=1)[:,None])*window;power=abs(np.fft.rfft(energy,axis=1))**2;total=power[:,1:].sum(axis=1)
  harmonic=[]
  for f in [1/12,2/12,3/12]:
   z=energy@np.exp(-2j*np.pi*f*t);coherence=abs(z.mean())/np.sqrt(np.mean(abs(z)**2))
   k=int(np.argmin(abs(freq-f)));fraction=power[:,max(1,k-1):k+2].sum(axis=1)/total
   harmonic.append({'frequency':f,'cross_seed_phase_coherence':float(coherence),'median_three_bin_power_fraction':float(np.median(fraction))})
  metrics.append({'mean_abs_reference_correlation':float(np.mean(abs(cs[0,1:]))),'mean_signed_reference_correlation':float(np.mean(cs[0,1:])),'former_clock_harmonics':harmonic})
 report['models'][name]=metrics
for item in d['sensitivity']:
 a=np.array(item['rows']);sv=np.linalg.svd(a,compute_uv=False);p=sv*sv/sum(sv*sv)
 report['sensitivity'].append({'seed':item['seed'],'local_rank':int(np.linalg.matrix_rank(a)),'condition_number':float(sv[0]/sv[-1]),'participation_ratio':float(1/sum(p*p)),'largest_variance_fraction':float(max(p))})
old=report['models']['legacy-full-seed'];new=report['models']['hyperfield']
assert all(m['mean_abs_reference_correlation']<.25 for m in new)
assert all(m['local_rank']==16 for m in report['sensitivity'])
assert all(m['largest_variance_fraction']<.5 for m in report['sensitivity'])
assert all(new[i]['former_clock_harmonics'][0]['cross_seed_phase_coherence']<old[i]['former_clock_harmonics'][0]['cross_seed_phase_coherence'] for i in range(3))
(ROOT/'rhythm-audit.json').write_text(json.dumps(report,indent=2))
print(json.dumps({'window0':{k:v[0] for k,v in report['models'].items()},'sensitivity':report['sensitivity']},indent=2))
