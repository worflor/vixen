import './extended.mjs';
import {
  Wide,
  baseline,
  calibrateOccupancy,
  couple,
  create,
  createCoherent,
  nextCrossing,
  polynomialGuide,
  restore,
  wideExp,
  type Config,
  type CurveJet,
  type InfluenceOptions,
  type NativeInstrument,
  type NormalizedSample,
  type Sample,
  type Snapshot,
} from '@worflor/vixen';

const options: Config = {
  seed: 'typed-world',
  stabilizer: 0.73123,
  velocity: new Float64Array(16),
};
const field: NativeInstrument = create(options);
const sample: Sample = field.sample(233.125);
const jet: CurveJet = field.jet(233.125);
const normalizedSample: NormalizedSample = field.sampleNormalized(233.125);
const branch = field.fork({ at: 200, operation: 'typed-door', handoffWidth: 24 });
const replayData: Snapshot = branch.snapshot();
const replay = restore(replayData);

const guide = polynomialGuide([0, 1, 0.25], { center: 200, domain: [100, 500] });
const influenceOptions: InfluenceOptions = {
  at: 120,
  amount: 0.7,
  handoffWidth: 24,
  reference: 'backbone',
  until: 420,
  releaseWidth: 30,
};
const guided = field.influence(guide, influenceOptions);
const additive = guided.influence(guide, { at: 160, amount: 0.2, mode: 'add' });
const event = additive.influenceAt(180);

const coherent = createCoherent({ seed: 23 });
const group = couple([field, coherent], 0.4, [220, 300]);
const groupValues = group.sample(250);
const groupJets = group.jet(250);
const calibration = calibrateOccupancy([-0.2, 0, { deviation: 0.1, weight: 2 }], {
  targetFraction: 0.5,
  outputThreshold: 0.5,
});
const crossing = nextCrossing(field, 0, 120, 400, { continuous: true });
const large = wideExp(1000);
const ratio = Wide.fromRatio(3n, 2n);
const value: number | null = large.toNumber();

function typeOnlyRejections(){
// @ts-expect-error Seeds are text or numeric uint32 values.
create({ seed: true });
// @ts-expect-error Unknown configuration keys are rejected.
create({ seed: 'x', mystery: 1 });
// @ts-expect-error A fork requires its activation coordinate and operation token.
field.fork({ at: 10 });
// @ts-expect-error Guide coefficients must be numbers.
polynomialGuide(['not numeric']);
// @ts-expect-error Restore accepts declared replay schemas, not arbitrary records.
restore({ algorithm: 'unknown', config: {} });
// @ts-expect-error wideExp accepts a binary64 number, not a bigint.
wideExp(1000n);

// @ts-expect-error Derivatives can be unavailable at a hard contact.
const unavailableSlope: number = jet.slope;
// @ts-expect-error Extended normalized values can be unavailable as scalar numbers.
const unavailableBase: number = normalizedSample.base;
}

console.log(
  branch.lineage[0]?.seed,
  field.domain,
  field.dips,
  sample.activity,
  jet.slope,
  normalizedSample.value,
  replay.sample(233.125).value,
  event.weight,
  groupValues.length,
  groupJets.length,
  calibration.achievedFraction,
  crossing.status,
  value,
  ratio.toJSON(),
  baseline(100),
);
