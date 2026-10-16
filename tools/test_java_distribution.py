"""Exercise the Java 17 distribution outside source-tree classpaths."""
from pathlib import Path
import json, subprocess, sys, tempfile, zipfile
ROOT=Path(__file__).resolve().parents[1]

def main():
    with tempfile.TemporaryDirectory(prefix='vixen-java-install-') as folder:
        target=Path(folder); jar=target/'vixen.jar'
        subprocess.run([sys.executable,str(ROOT/'tools/build_java.py'),'--output',str(jar)],cwd=ROOT,check=True)
        with zipfile.ZipFile(jar) as package:
            names=package.namelist()
            assert 'dev/woflo/vixen/Vixen.class' in names
            assert 'META-INF/LICENSE' in names
            assert not any(name.endswith('Run.class') for name in names)
            header=package.read('dev/woflo/vixen/Vixen.class')
            assert int.from_bytes(header[6:8],'big')==61,'The JAR must target Java17'
        source='''import dev.woflo.vixen.*;
public final class InstalledProbe {
 public static void main(String[] args) {
  Curve curve=Vixen.createCoherent().impulse(120,.1,20).fork(160,"installed",8.0);
  Curve replay=Vixen.restoreCurve(curve.snapshotJson());
  if(!curve.sample(180).equals(replay.sample(180)))throw new AssertionError("Installed replay differs");
  if(Wide.fromDouble(2).mul(Wide.fromDouble(3)).toDouble()!=6)throw new AssertionError("Installed wide arithmetic differs");
  Extended.Signal a=Vixen.createExtended(),b=Vixen.createExtended(Config.defaults().withSeed(Seed.of(2)));
  Extended.Signal guided=ExtendedOps.influence(a,ExtendedOps.polynomial(new double[]{1},0,1,1,Extended.MAX_DOMAIN_END),java.util.Map.of("at",120,"amount",1,"handoffWidth",1,"reference","value"));
  if(!guided.sampleNormalized(1e9).get("value").equals(1.0))throw new AssertionError("Installed tiny guide lost");
  ExtendedOps.Group group=ExtendedOps.couple(java.util.List.of(a,b),.5,1e9,1e9+100);
  Extended.Signal selected=group.curves().get(0),restored=(Extended.Signal)Vixen.restore(selected.snapshotJson());
  if(!selected.sampleNormalized(1e9+25).equals(restored.sampleNormalized(1e9+25)))throw new AssertionError("Installed extended graph differs");
  System.out.println("Installed Java17 JAR: coherent/extended composition, fork, replay and scaled arithmetic passed");
 }
}'''
        (target/'InstalledProbe.java').write_text(source,encoding='utf-8')
        subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',str(jar),'InstalledProbe.java'],cwd=target,check=True)
        subprocess.run(['java','-cp',str(jar)+__import__('os').pathsep+str(target),'InstalledProbe'],cwd=target,check=True)
if __name__=='__main__':main()
