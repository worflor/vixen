package dev.woflo.vixen;
public final class AnalysisPortRun {
 public static void main(String[] args){
  OccupancyPopulation population=OccupancyPopulation.unweighted(-2,-1,0,1);
  Occupancy.Calibration calibration=Occupancy.calibrate(population,.5,.5);
  if(calibration.achievedFraction()!=.5||calibration.certified())throw new AssertionError("Occupancy measurement or evidence is wrong");
  Occupancy.Calibration replay=Occupancy.restore(calibration.snapshotJson(),population);
  if(replay.evaluate(1)!=calibration.evaluate(1))throw new AssertionError("Occupancy replay differs");
  try{Occupancy.restore(calibration.snapshotJson(),OccupancyPopulation.unweighted(2,3));throw new AssertionError("Wrong population binding accepted");}catch(IllegalArgumentException expected){}
  double[][] matrix={{1,.5},{.5,1}};
  Correlation.Factorization factor=Correlation.validateCorrelationMatrix(matrix);
  if(factor.factorError()>factor.tolerance())throw new AssertionError("Factor outside its declared tolerance");
  try{Correlation.validateCorrelationMatrix(new double[][]{{1,1,1},{1,1,-1},{1,-1,1}});throw new AssertionError("Indefinite matrix accepted");}catch(IllegalArgumentException expected){}
  Correlation.validateCorrelationMatrix(new double[][]{{1,1},{1,1}});
  Channels.CorrelatedChannels group=Channels.createVixenChannels(matrix,Config.defaults(),Seed.of("channels"),Channels.Options.defaults());
  if(!java.util.Arrays.equals(group.sample(150),group.sample(150)))throw new AssertionError("Seeded channels changed");
  System.out.println("Java declared-population occupancy, binding, exact PSD and seeded channels passed");
 }
}
