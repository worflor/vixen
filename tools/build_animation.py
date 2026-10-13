"""Render actual sampled seeds; fixed axes, hard cuts, no seed interpolation."""
from pathlib import Path
import json,sys
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT))
from vixen import Vixen, ALGORITHM

SEEDS=['vixen',0,1,2,3,4]
LEVELS=[0,.5,1]
COLORS=['#f2a36b','#b6a2ff','#72d7c4']
W,H,SCALE=1000,500,2

def font(size,bold=False):
    candidates=['C:/Windows/Fonts/'+('seguisb.ttf' if bold else 'segoeui.ttf'),'/usr/share/fonts/truetype/dejavu/DejaVuSans'+('-Bold' if bold else '')+'.ttf']
    for path in candidates:
        if Path(path).exists():return ImageFont.truetype(path,size*SCALE)
    raise RuntimeError('Provide Segoe UI or DejaVu Sans fonts')

samples=[]
for seed in SEEDS:
    rows=[]
    for s in LEVELS:
        c=Vixen(seed=seed,stabilizer=s)
        rows.append([c.sample(1+i/4)['value'] for i in range(1197)])
    samples.append(rows)
values=[v for rows in samples for row in rows for v in row[396:]]
lo=min(0,min(values));hi=max(values);pad=(hi-lo)*.05;lo-=pad;hi+=pad
frames=[]
for index,(seed,rows) in enumerate(zip(SEEDS,samples)):
    im=Image.new('RGB',(W*SCALE,H*SCALE),'#0d121b');d=ImageDraw.Draw(im)
    def text(x,y,t,size=16,color='#b6c1d0',bold=False):d.text((x*SCALE,y*SCALE),t,font=font(size,bold),fill=color)
    def line(points,color,width=1):d.line([(x*SCALE,y*SCALE) for x,y in points],fill=color,width=width*SCALE)
    text(28,22,'One opening. Different futures.',27,'#edf1f7',True)
    text(740,28,'SEED '+json.dumps(seed),21,'#edf1f7',True)
    text(50,88,'1–100  ·  same opening',19,'#d8c192',True)
    text(358,88,'100+  ·  seeded continuation',19,'#edf1f7',True)
    a,b,top,bottom=50,300,130,415
    for i in range(5):
        y=top+(bottom-top)*i/4;line([(a,y),(b,y)],'#293140');line([(358,y),(968,y)],'#293140')
    # Separate zoom; index 396 (x=100) belongs to both panels to show their shared seam.
    line([(a+i/396*(b-a),bottom-rows[0][i]/300*(bottom-top)) for i in range(397)],'#d8c192',3)
    for row,color in zip(rows,COLORS):
        line([(358+(i-396)/800*610,top+(hi-row[i])/(hi-lo)*(bottom-top)) for i in range(396,1197)],color,2)
    zero=top+hi/(hi-lo)*(bottom-top);line([(358,zero),(968,zero)],'#5c697b')
    for x,t,align in [(a,'1','left'),(b,'100','right'),(358,'100','left'),(663,'200','center'),(968,'300','right')]:
        width=d.textlength(t,font=font(14))/SCALE
        offset=width/2 if align=='center' else width if align=='right' else 0
        text(x-offset,bottom+9,t,14)
    text(358,112,'raw value · fixed axes across all seeds',13)
    text(50,112,'zoom · raw value 0–300',13)
    for x,y in [(300,bottom-rows[0][396]/300*(bottom-top)),(358,top+(hi-rows[0][396])/(hi-lo)*(bottom-top))]:
        d.ellipse(((x-4)*SCALE,(y-4)*SCALE,(x+4)*SCALE,(y+4)*SCALE),fill='#d8c192')
    text(358,zero-20,'0',13)
    for x,label,color in zip([50,320,640],['wild · s=0','stabilized · s=0.5','backbone · s=1'],COLORS):
        line([(x,475),(x+27,475)],color,3);text(x+38,462,label,17,color)
    frames.append(im.resize((W,H),Image.Resampling.LANCZOS))

out=ROOT/'docs/media/seed-gallery.gif';out.parent.mkdir(parents=True,exist_ok=True)
palette=frames[0].quantize(colors=256,dither=Image.Dither.NONE)
encoded=[f.quantize(palette=palette,dither=Image.Dither.NONE) for f in frames]
encoded[0].save(out,save_all=True,append_images=encoded[1:],duration=1600,loop=0,disposal=2,optimize=False)
with Image.open(out) as check:
    assert check.n_frames==len(SEEDS) and check.info['loop']==0
print(json.dumps({'file':out.name,'frames':len(frames),'bytes':out.stat().st_size,'algorithm':ALGORITHM,'seeds':SEEDS,'hold_ms':1600,'fixed_y_limits':[lo,hi],'samples_per_curve':1197}))
(ROOT/'.smoke').mkdir(exist_ok=True)
frames[0].save(ROOT/'.smoke/seed-gallery-first.png')
frames[2].save(ROOT/'.smoke/seed-gallery-third.png')
