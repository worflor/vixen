"""Compile every Java runtime/test source for Java 17 and run executable suites."""
from pathlib import Path
import subprocess,tempfile

ROOT=Path(__file__).resolve().parents[1]

def main():
    sources=sorted(str(p) for folder in (ROOT/'java/src',ROOT/'java/test') for p in folder.rglob('*.java'))
    suites=sorted('dev.woflo.vixen.'+p.stem for p in (ROOT/'java/test/dev/woflo/vixen').glob('*Run.java'))
    if not sources or not suites:raise RuntimeError('Java sources and executable suites are required')
    with tempfile.TemporaryDirectory(prefix='vixen-java-') as build:
        subprocess.run(['javac','--release','17','-encoding','UTF-8','-d',build,*sources],cwd=ROOT,check=True,timeout=90)
        for suite in suites:subprocess.run(['java','-cp',build,suite],cwd=ROOT,check=True,timeout=60)
    print('All Java executable suites passed:',len(suites))

if __name__=='__main__':main()
