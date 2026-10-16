"""Build a dependency-free Vixen JAR targeting Java 17."""
from pathlib import Path
import argparse, json, shutil, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[1]

def build(output):
    output=Path(output).resolve();output.parent.mkdir(parents=True,exist_ok=True)
    sources=sorted((ROOT/'java/src').rglob('*.java'))
    if not sources:raise RuntimeError('Java sources are missing')
    for program in ['javac','jar']:
        if not shutil.which(program):raise RuntimeError(program+' is required')
    with tempfile.TemporaryDirectory(prefix='vixen-jar-build-') as folder:
        target=Path(folder);classes=target/'classes';classes.mkdir()
        subprocess.run(['javac','--release','17','-encoding','UTF-8','-d',str(classes),*[str(p) for p in sources]],cwd=ROOT,check=True)
        resources=classes/'META-INF';resources.mkdir()
        shutil.copyfile(ROOT/'LICENSE',resources/'LICENSE')
        version=json.loads((ROOT/'package.json').read_text(encoding='utf-8'))['version']
        manifest=target/'MANIFEST.MF'
        manifest.write_text('Manifest-Version: 1.0\nImplementation-Title: Vixen\nImplementation-Version: '+version+'\nAutomatic-Module-Name: dev.woflo.vixen\n\n',encoding='utf-8')
        subprocess.run(['jar','--create','--file',str(output),'--manifest',str(manifest),'-C',str(classes),'.'],cwd=ROOT,check=True)
    print(str(output));return output

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,default=ROOT/'dist/vixen.jar');args=parser.parse_args();build(args.output)
if __name__=='__main__':main()
