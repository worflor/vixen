export const ALGORITHM: 'vixen-hyperfield-c2/4';
export const COHERENT_ALGORITHM: 'vixen-hyperfield-c2/5';
export const COHERENT_TAIL_ALGORITHM: 'vixen-tail-c2/3';
export const TAIL_ALGORITHM: 'vixen-tail-c2/2';
export const INFLUENCE_ALGORITHM: 'vixen-influence-c2/1';
export const INFLUENCE_ALGORITHM_V2: 'vixen-influence-c2/2';
export const INFLUENCE_ALGORITHM_V3: 'vixen-influence-c2/3';
export const OPERATION_ALGORITHM: 'vixen-operation-c2/1';
export const GRAPH_ALGORITHM: 'vixen-graph/1';
export const DIMENSIONS: 16;
export const FEATURES: 48;
export type Pair = readonly [number, number];
export type Vector = readonly number[] | Int8Array | Uint8Array | Uint8ClampedArray | Int16Array | Uint16Array | Int32Array | Uint32Array | Float32Array | Float64Array;
export type Floor = 'none' | 'zero' | 'baseline' | 'backbone';
export type Seed = string | number;
export interface Config {
  openingEndpoint?: number;
  introVariation?: number;
  floor?: Floor;
  ceilingMargin?: number | null;
  boundarySoftness?: number;
  seed?: Seed;
  stabilizer?: number;
  strength?: number;
  ramp?: number;
  tempo?: number;
  contrast?: number;
  latent?: Vector;
  velocity?: Vector;
}
export interface ResolvedConfig {
  readonly openingEndpoint: number;
  readonly introVariation: number;
  readonly floor: Floor;
  readonly ceilingMargin: number | null;
  readonly boundarySoftness: number;
  readonly seed: Seed;
  readonly stabilizer: number;
  readonly strength: number;
  readonly ramp: number;
  readonly tempo: number;
  readonly contrast: number;
  readonly latent: readonly number[];
  readonly velocity: readonly number[];
}
export const DEFAULTS: ResolvedConfig;
export interface Sample {
  readonly unconstrainedValue: number;
  readonly unconstrainedDeviation: number;
  readonly unconstrainedGuideValue: number;
  readonly unconstrainedGuideDeviation: number;
  readonly floorValue: number | null;
  readonly ceilingValue: number | null;
  readonly boundaryWidth: number;
  readonly constraintApplied: boolean;
  readonly x: number;
  readonly value: number;
  readonly base: number;
  readonly score: number;
  readonly guideScore: number;
  readonly rawDeviation: number;
  readonly guideDeviation: number;
  readonly guideValue: number;
  readonly stabilizationWeight: number;
  readonly deviation: number;
  readonly multiplier: number;
  readonly activity: number;
  readonly positiveMultiplier: number;
  readonly positiveValue: number;
  readonly gate: number;
}
export interface Ridge {
  readonly index: number;
  readonly direction: readonly number[];
  readonly scale: number;
  readonly basePhase: number;
  readonly phase: number;
  readonly slope: number;
  readonly guidePhase: number;
  readonly guideSlope: number;
  readonly channel: number;
}
export interface Contribution {
  readonly sourceSeed?: Seed;
  readonly index: number;
  readonly scale: number;
  readonly coordinate: number;
  readonly cell: number;
  readonly contribution: number;
  readonly guideContribution: number;
}
export interface Bounds {
  readonly unconstrainedSpatialSlope: number;
  readonly score: Pair;
  readonly deviation: Pair;
  readonly multiplier: Pair;
  readonly activity: Pair;
  readonly positiveMultiplier: Pair;
}
export interface FieldJet { readonly value: number; readonly d1: number | null; readonly d2: number | null; }
export interface CurveJet {
  readonly sample: Sample;
  readonly slope: number | null;
  readonly curvature: number | null;
  readonly fields: Readonly<Record<Exclude<keyof Sample, 'constraintApplied' | 'floorValue' | 'ceilingValue'>, FieldJet> & Partial<Record<'floorValue' | 'ceilingValue', FieldJet>>>;
  readonly availability: Readonly<{slope: boolean; curvature: boolean}>;
  readonly differentiability: Readonly<{value: boolean; sample: boolean; rationale: string | null}>;
}
export interface NormalizedSample extends Omit<Sample, 'value' | 'base' | 'guideValue' | 'unconstrainedValue' | 'unconstrainedGuideValue' | 'positiveValue' | 'boundaryWidth'> {
  readonly value: number | null; readonly base: number | null; readonly guideValue: number | null;
  readonly unconstrainedValue: number | null; readonly unconstrainedGuideValue: number | null;
  readonly positiveValue: number | null; readonly boundaryWidth: number | null;
  readonly logBase: number;
  readonly signedLogValue: Readonly<{sign: -1 | 0 | 1; logAbs: number | null}>;
  readonly floorRatio?: number | null; readonly ceilingRatio?: number | null; readonly boundaryWidthRatio?: number;
}
export interface ForkOptions {
  readonly at: number;
  readonly operation: string;
  readonly handoffWidth?: number;
}
export interface ForkEvent extends ForkOptions {
  readonly seed: number;
}
export type NativeAlgorithm = typeof ALGORITHM | typeof COHERENT_ALGORITHM;
export type CurveAlgorithm = NativeAlgorithm | typeof INFLUENCE_ALGORITHM | typeof INFLUENCE_ALGORITHM_V2 | typeof INFLUENCE_ALGORITHM_V3 | typeof OPERATION_ALGORITHM;
export interface Instrument {
  readonly algorithm: CurveAlgorithm | typeof TAIL_ALGORITHM | typeof COHERENT_TAIL_ALGORITHM;
  readonly config: ResolvedConfig;
  readonly bounds: Bounds;
  readonly domain: Pair;
  readonly dips: readonly Readonly<{ center: number; width: number; depth: number }>[];
  readonly lineage: readonly ForkEvent[];
  readonly influences?: readonly InfluenceEntry[];
  readonly influenceAt?: (x: number) => InfluenceAtResult;
  sample(x: number): Sample;
  at(x: number, coordinates: Vector): Sample;
  location(x: number): readonly number[];
  inspect(x: number): readonly Contribution[];
  ridges(): readonly Ridge[];
  jet(x: number, coordinates?: Vector): CurveJet;
  slope(x: number, coordinates?: Vector): number | null;
  curvature(x: number, coordinates?: Vector): number | null;
  derivative(x: number, coordinates?: Vector): number | null;
  secondDerivative(x: number, coordinates?: Vector): number | null;
  sampleRange(x0: number, x1: number, count?: number): readonly Sample[];
  fork(options: ForkOptions): Instrument;
  snapshot(): Snapshot;
  tail(from: number): Snapshot;
  influence(target: GuideTarget, options?: InfluenceOptions): InfluencedInstrument;
  impulse(options: ImpulseOptions): OperationInstrument;
  impulse(at: number, magnitude: number, relaxation: number): OperationInstrument;
  redistribute(window: Pair, shape: RedistributionShape): OperationInstrument;
  nextCrossing(threshold: number, from: number, horizon: number, options?: CrossingOptions): CrossingResult;
  encloseDeviation?(lo: number, hi: number, coordinates?: Vector): Pair;
}
export interface NativeInstrument extends Instrument {
  readonly algorithm: NativeAlgorithm | typeof TAIL_ALGORITHM | typeof COHERENT_TAIL_ALGORITHM;
  readonly normalizedDomain: Pair;
  snapshot(): BaseSnapshot | TailSnapshot;
  tail(from: number): TailSnapshot;
  sampleNormalized(x: number): NormalizedSample;
  atNormalized(x: number, coordinates: Vector): NormalizedSample;
  encloseDeviation(lo: number, hi: number, coordinates?: Vector): Pair;
  fork(options: ForkOptions): NativeInstrument;
}
export interface InfluencedInstrument extends Instrument {
  readonly algorithm: typeof INFLUENCE_ALGORITHM | typeof INFLUENCE_ALGORITHM_V2 | typeof INFLUENCE_ALGORITHM_V3;
  readonly influences: readonly InfluenceEntry[];
  influenceAt(x: number): InfluenceAtResult;
  influence(target: GuideTarget, options?: InfluenceOptions): InfluencedInstrument;
}
export interface OperationInstrument extends Instrument {
  readonly algorithm: typeof OPERATION_ALGORITHM;
}
export interface CoupledInstrument extends Instrument {
  readonly algorithm: typeof OPERATION_ALGORITHM;
  fork(options: ForkOptions): never;
}
export type Curve = Instrument;
export interface BaseSnapshot {
  readonly algorithm: NativeAlgorithm | 'vixen-hyperfield-c2/1' | 'vixen-hyperfield-c2/2' | 'vixen-hyperfield-c2/3';
  readonly config: Config;
  readonly lineage?: readonly ForkOptions[];
}
export interface TailSnapshot {
  readonly algorithm: typeof TAIL_ALGORITHM | typeof COHERENT_TAIL_ALGORITHM;
  readonly from: number;
  readonly config: ResolvedConfig;
  readonly seed: Seed;
  readonly leafSeed: number;
  readonly foldedCount: number;
  readonly witness: string;
  readonly lineage: readonly ForkOptions[];
}
export interface PolynomialGuide {
  readonly format: 'vixen-guide/1';
  readonly kind: 'polynomial';
  readonly coefficients: readonly number[];
  readonly center: number;
  readonly scale: number;
  readonly domain: Pair;
}
export interface GuideJet {
  readonly x: number;
  readonly y: number;
  readonly d1: number;
  readonly d2: number;
}
export interface SampledGuide {
  readonly format: 'vixen-guide/1';
  readonly kind: 'quintic';
  readonly knots: readonly GuideJet[];
}
export type Guide = PolynomialGuide | SampledGuide;
export interface CompiledGuide {
  readonly spec: Guide;
  readonly domain: Pair;
  evaluate(x: number): number;
}
export type GuideTarget = Guide | ((x: number) => number);
export type InfluenceReference = 'value' | 'baseline' | 'backbone';
export type InfluenceMode = 'blend' | 'add';
export interface InfluenceBaseOptions {
  readonly at?: number;
  readonly amount?: number;
  readonly handoffWidth?: number;
  readonly count?: number;
  readonly reference?: InfluenceReference;
  readonly mode?: InfluenceMode;
}
export type InfluenceOptions = InfluenceBaseOptions & ({
  readonly until?: never;
  readonly releaseWidth?: never;
} | {
  readonly until: number;
  readonly releaseWidth: number;
});
export interface InfluenceEntryBase {
  readonly at: number;
  readonly amount: number;
  readonly handoffWidth: number;
  readonly guide: Guide;
}
export interface InfluenceEntryV1 extends InfluenceEntryBase {
  readonly reference?: never;
  readonly mode?: never;
  readonly until?: never;
  readonly releaseWidth?: never;
}
export interface InfluenceEntryExtended extends InfluenceEntryBase {
  readonly reference: InfluenceReference;
  readonly mode?: 'blend';
  readonly until?: number;
  readonly releaseWidth?: number;
}
export interface InfluenceEntryAdditive extends InfluenceEntryBase {
  readonly reference: InfluenceReference;
  readonly mode: 'add';
  readonly until?: number;
  readonly releaseWidth?: number;
}
export type InfluenceEntry = InfluenceEntryV1 | InfluenceEntryExtended | InfluenceEntryAdditive;
export interface BlendInfluenceAtResult {
  readonly weight: number;
  readonly targetValue: number | null;
  readonly sourceValue: number;
  readonly followingError: number | null;
}
export interface AdditiveInfluenceAtResult {
  readonly mode: 'add';
  readonly weight: number;
  readonly targetValue: null;
  readonly sourceValue: number;
  readonly followingError: null;
  readonly increment: number;
  readonly deviationIncrement: number;
}
export type InfluenceAtResult = BlendInfluenceAtResult | AdditiveInfluenceAtResult;
export interface InfluenceSnapshotV1 {
  readonly algorithm: typeof INFLUENCE_ALGORITHM;
  readonly curve: Snapshot;
  readonly guide: Guide;
  readonly at: number;
  readonly amount: number;
  readonly handoffWidth: number;
}
export interface PersistentInfluenceSnapshotV2 {
  readonly algorithm: typeof INFLUENCE_ALGORITHM_V2;
  readonly curve: Snapshot;
  readonly guide: Guide;
  readonly at: number;
  readonly amount: number;
  readonly handoffWidth: number;
  readonly reference: 'baseline' | 'backbone';
  readonly until?: never;
  readonly releaseWidth?: never;
}
export interface WindowedInfluenceSnapshotV2 {
  readonly algorithm: typeof INFLUENCE_ALGORITHM_V2;
  readonly curve: Snapshot;
  readonly guide: Guide;
  readonly at: number;
  readonly amount: number;
  readonly handoffWidth: number;
  readonly reference: InfluenceReference;
  readonly until: number;
  readonly releaseWidth: number;
}
export interface InfluenceSnapshotV3 {
  readonly algorithm: typeof INFLUENCE_ALGORITHM_V3;
  readonly curve: Snapshot;
  readonly guide: Guide;
  readonly at: number;
  readonly amount: number;
  readonly handoffWidth: number;
  readonly reference: InfluenceReference;
  readonly mode: 'add';
  readonly until?: number;
  readonly releaseWidth?: number;
}
export type InfluenceSnapshot = InfluenceSnapshotV1 | PersistentInfluenceSnapshotV2 | WindowedInfluenceSnapshotV2 | InfluenceSnapshotV3;
export interface ImpulseOptions {
  readonly at: number;
  readonly magnitude: number;
  readonly relaxation: number;
  readonly attack?: number;
}
export interface RedistributionShapeOptions {
  readonly coefficients: readonly number[];
  readonly magnitude?: number;
  readonly measure?: 'actualDeviation' | 'unconstrainedDeviation';
}
export type RedistributionShape = readonly number[] | RedistributionShapeOptions;
export interface RedistributionRecipe {
  readonly type: 'redistribution';
  readonly from: number;
  readonly until: number;
  readonly coefficients: readonly number[];
  readonly magnitude: number;
  readonly measure: 'actualDeviation' | 'unconstrainedDeviation';
  readonly certificate?: RedistributionCertificate;
}
export interface RedistributionCertificate {
  readonly kind: 'exact-real-endpoint-integral';
  readonly potential: 't^4*(1-t)^4*P(t)';
  readonly perturbation: 'd/dt potential';
  readonly integral: 'Q(1)-Q(0)=0';
}
export interface ImpulseRecipe extends ImpulseOptions {
  readonly type: 'impulse';
}
export type PerturbationOptions = ImpulseRecipe | RedistributionRecipe;
export interface PerturbationJet {
  readonly value: number;
  readonly first: number;
  readonly second: number;
}
export interface ImpulseOperationSnapshot {
  readonly algorithm: typeof OPERATION_ALGORITHM;
  readonly operation: 'impulse';
  readonly curve: Snapshot;
  readonly recipe: ImpulseRecipe;
}
export interface RedistributionOperationSnapshot {
  readonly algorithm: typeof OPERATION_ALGORITHM;
  readonly operation: 'redistribution';
  readonly curve: Snapshot;
  readonly recipe: RedistributionRecipe;
}
export interface CouplingOperationSnapshot {
  readonly algorithm: typeof OPERATION_ALGORITHM;
  readonly operation: 'coupling';
  readonly curves: readonly Snapshot[];
  readonly strength: number;
  readonly window: Pair;
}
export interface CouplingViewOperationSnapshot {
  readonly algorithm: typeof OPERATION_ALGORITHM;
  readonly operation: 'couplingView';
  readonly group: Snapshot;
  readonly index: number;
}
export type OperationSnapshot = ImpulseOperationSnapshot | RedistributionOperationSnapshot | CouplingOperationSnapshot | CouplingViewOperationSnapshot;
export type GraphReferenceFrame<T> = T extends {readonly curve: Snapshot} ? Omit<T, 'curve'> & {readonly curve: number} : T extends {readonly curves: readonly Snapshot[]} ? Omit<T, 'curves'> & {readonly curves: readonly number[]} : T extends {readonly group: Snapshot} ? Omit<T, 'group'> & {readonly group: number} : T;
export type GraphNode = BaseSnapshot | TailSnapshot | ExtendedSnapshot | ExtendedTailSnapshot | GraphReferenceFrame<InfluenceSnapshot | OperationSnapshot | ExtendedOperationSnapshot | ExtendedCouplingSnapshot | ExtendedCouplingViewSnapshot>;
export interface GraphSnapshot {
  readonly algorithm: typeof GRAPH_ALGORITHM;
  readonly nodes: readonly GraphNode[];
  readonly root: number;
}
export type Snapshot = BaseSnapshot | TailSnapshot | InfluenceSnapshot | OperationSnapshot | GraphSnapshot | ExtendedSnapshot | ExtendedTailSnapshot | ExtendedOperationSnapshot | ExtendedCouplingSnapshot | ExtendedCouplingViewSnapshot;
export function polynomialGuide(coefficients: readonly number[], options?: { center?: number; scale?: number; domain?: Pair }): PolynomialGuide;
export function taylorGuide(derivatives: readonly number[], options?: { center?: number; domain?: Pair }): PolynomialGuide;
export function sampledGuide(knots: readonly GuideJet[]): SampledGuide;
export function captureGuide(fn: (x: number) => number, options?: { domain?: Pair; count?: number; derivative?: (x: number) => number; curvature?: (x: number) => number }): SampledGuide;
export function compileGuide(guide: Guide): CompiledGuide;
export function evaluateGuide(guide: Guide, x: number): number;
export function create(options?: Config): NativeInstrument;
export function createCoherent(options?: Config): NativeInstrument;
export function restore(snapshot: BaseSnapshot | TailSnapshot): NativeInstrument;
export function restore(snapshot: CouplingOperationSnapshot): CoupledGroup;
export function restore(snapshot: ExtendedSnapshot | ExtendedTailSnapshot): ExtendedInstrument;
export function restore(snapshot: ExtendedOperationSnapshot | ExtendedCouplingViewSnapshot): ExtendedComposition;
export function restore(snapshot: ExtendedCouplingSnapshot): ExtendedCoupledGroup;
export function restore(snapshot: Snapshot): Instrument | CoupledGroup | ExtendedInstrument | ExtendedComposition | ExtendedCoupledGroup;
export function seed32(seed: Seed): number;
export function hash32(seed: number, index: number, channel: number): number;
export function smooth5(t: number): number;
export function bump(t: number): number;
export function baseline(x: number): number;
export function navigationBasis(index?: number): Readonly<{ u: readonly number[]; v: readonly number[] }>;
export function boundaryRamp(t: number): number;
export interface IntervalEnclosureOptions {
  readonly enclose?: Enclosure;
  readonly enclosePoint?: PointEnclosure;
  readonly evaluate?: (x: number) => number | Readonly<Record<string, number>>;
  readonly measure?: string;
  readonly domain?: Pair;
  readonly continuous?: boolean;
  readonly tolerance?: number;
  readonly maxIntervals?: number;
  readonly maxIterations?: number;
  readonly maxDepth?: number;
}
export type Enclosure = (lo: number, hi: number, measure?: string) => Pair;
export type PointEnclosure = (lo: number, hi: number, measure?: string) => Pair;
export type CrossingOptions = IntervalEnclosureOptions;
export interface CrossingUnresolvedInterval {
  readonly interval: Pair;
  readonly reason: string;
}
export interface CrossingFound {
  readonly status: 'found';
  readonly interval: Pair;
  readonly endpointValues: Pair;
  readonly endpointEnclosures: readonly [Pair, Pair];
  readonly certifiedBefore: Pair;
  readonly certifiedBeforeUpperExclusive: true;
  readonly unresolved: readonly [];
  readonly enclosureChecks: number;
  readonly pointChecks: number;
  readonly continuousAssumed: boolean;
  readonly pointIsolated: boolean;
}
export interface CrossingAbsent {
  readonly status: 'absent';
  readonly interval: Pair;
  readonly certifiedAbsent: Pair;
  readonly unresolved: readonly [];
  readonly enclosureChecks: number;
  readonly pointChecks: number;
}
export interface CrossingUnresolved {
  readonly status: 'unresolved';
  readonly reason: 'enclosure-unavailable' | 'evaluator-unavailable' | 'budget' | 'continuity-required' | 'depth' | 'machine-spacing';
  readonly interval: Pair;
  readonly certifiedBefore: Pair;
  readonly certifiedBeforeUpperExclusive: true;
  readonly unresolved: readonly CrossingUnresolvedInterval[];
  readonly enclosureChecks: number;
  readonly pointChecks: number;
}
export type CrossingResult = CrossingFound | CrossingAbsent | CrossingUnresolved;
export interface CrossingSource {
  readonly domain?: Pair;
  sample?(x: number): Sample;
  readonly encloseDeviation?: (lo: number, hi: number) => Pair;
  readonly enclose?: Enclosure;
  readonly enclosureProvider?: Enclosure;
  readonly enclosePoint?: PointEnclosure;
}
export function nextCrossing(curve: CrossingSource | ((x: number) => number), threshold: number, from: number, horizon: number, options?: CrossingOptions): CrossingResult;
export function makeLipschitzEnclosure(evaluate: (x: number) => number, bound: number, pointError?: number): Enclosure;
export interface PopulationRow {
  readonly deviation: number;
  readonly weight: number;
}
export type PopulationEntry = number | PopulationRow;
export interface OccupancyOptions {
  readonly targetFraction: number;
  readonly outputThreshold: number;
  readonly slope?: number;
  readonly scale?: number;
}
export type OccupancyTieHandling = 'inclusive-at-cutoff' | 'values-at-or-above-cutoff';
export interface OccupancyRecipe {
  readonly format: 'vixen-occupancy/1';
  readonly targetFraction: number;
  readonly outputThreshold: number;
  readonly slope: number;
  readonly scale: number;
  readonly cutoff: number;
  readonly center: number;
  readonly totalPopulation: number;
  readonly totalWeight: number;
  readonly achievedFraction: number;
  readonly tieHandling: OccupancyTieHandling;
}
export interface OccupancyCalibration {
  readonly recipe: OccupancyRecipe;
  readonly targetFraction: number;
  readonly achievedFraction: number;
  readonly difference: number;
  readonly cutoff: number;
  readonly tieHandling: OccupancyTieHandling;
  readonly totalPopulation: number;
  readonly totalWeight: number;
  evaluate(deviation: number): number;
  serialize(): OccupancyRecipe;
}
export interface OccupancyValidation {
  readonly targetFraction: number;
  readonly achievedFraction: number;
  readonly difference: number;
  readonly absoluteError: number;
  readonly totalPopulation: number;
  readonly totalWeight: number;
  readonly highPopulation: number;
  readonly highWeight: number;
}
export function calibrateOccupancy(population: readonly PopulationEntry[], options: OccupancyOptions): OccupancyCalibration;
export function validateOccupancy(calibration: OccupancyCalibration | OccupancyRecipe, holdout: readonly PopulationEntry[]): OccupancyValidation;
export function restoreOccupancy(recipe: OccupancyRecipe): OccupancyCalibration;
export interface CorrelationFactor {
  readonly matrix: readonly (readonly number[])[];
  readonly factor: readonly (readonly number[])[];
  readonly tolerance: number;
  readonly factorError: number;
  readonly validation: 'exact-binary64-ldlt';
}
export function validateCorrelationMatrix(input: readonly (readonly number[])[], tolerance?: number): CorrelationFactor;
export interface CorrelatedSourceValue {
  readonly score?: number;
  readonly unconstrainedDeviation?: number;
}
export interface CorrelatedSource {
  readonly domain: Pair;
  sample(x: number): Sample | CorrelatedSourceValue;
  at(x: number, coordinates: Vector): Sample | CorrelatedSourceValue;
}
export interface CorrelatedChannelsOptions {
  readonly channel?: 'score' | 'unconstrainedDeviation';
  readonly tolerance?: number;
}
export interface CorrelatedChannelsRecipe {
  readonly format: 'vixen-correlated-channels/1';
  readonly matrix: readonly (readonly number[])[];
  readonly factor: readonly (readonly number[])[];
  readonly tolerance: number;
  readonly channel: 'score' | 'unconstrainedDeviation';
  readonly rootSeed: Readonly<{ type: 'text' | 'uint32'; value: Seed }>;
  readonly derivedSeeds: readonly number[];
  readonly seedDerivation: 'hash32-domain-separated/1';
}
export interface CorrelatedChannels {
  readonly domain: Pair;
  readonly matrix: readonly (readonly number[])[];
  readonly factor: readonly (readonly number[])[];
  readonly channel: 'score' | 'unconstrainedDeviation';
  readonly sourceSeeds: readonly number[];
  readonly sourceConfigs: readonly Readonly<{ seed: number; channel: 'score' | 'unconstrainedDeviation' }>[];
  readonly recipe: CorrelatedChannelsRecipe;
  sample(x: number): readonly number[];
  at(x: number, coordinates: Vector): readonly number[];
  serialize(): CorrelatedChannelsRecipe;
}
export function createCorrelatedChannels(inputMatrix: readonly (readonly number[])[], createSource: (seed: number) => CorrelatedSource, rootSeed: Seed, options?: CorrelatedChannelsOptions): CorrelatedChannels;
export function measureChannelCorrelation(group: CorrelatedChannels | ((x: number) => readonly number[]), points: readonly number[]): readonly (readonly number[])[];
export interface CoupledGroup {
  readonly algorithm: typeof OPERATION_ALGORITHM;
  readonly strength: number;
  readonly window: Pair;
  readonly domain: Pair;
  readonly curves: readonly CoupledInstrument[];
  sample(x: number): readonly Sample[];
  at(x: number, coordinates: Vector): readonly Sample[];
  jet(x: number, coordinates?: Vector): readonly CurveJet[];
  sampleRange(from: number, until: number, count?: number): readonly (readonly Sample[])[];
  snapshot(): Snapshot;
  tail(from: number): Snapshot;
  fork(): never;
}
export function couple(curves: readonly Instrument[], strength: number, window: Pair): CoupledGroup;
export function perturbationJet(options: PerturbationOptions, x: number): PerturbationJet;
export interface WideJSON {
  readonly format: typeof WIDE_FORMAT;
  readonly sign: -1 | 0 | 1;
  readonly significand: number;
  readonly exponent2: string;
}
export const WIDE_FORMAT: 'vixen-scaled-binary64/1';
export class Wide {
  constructor(significand: bigint, exponent?: bigint | number);
  readonly m: bigint;
  readonly e: bigint;
  readonly sign: -1 | 0 | 1;
  static fromNumber(value: number): Wide;
  static fromRatio(numerator: bigint, denominator: bigint, power?: bigint | number): Wide;
  static from(value: number | Wide): Wide;
  static decode(value: WideJSON): Wide;
  neg(): Wide;
  abs(): Wide;
  scale(power: bigint | number): Wide;
  add(other: number | Wide): Wide;
  sub(other: number | Wide): Wide;
  mul(other: number | Wide): Wide;
  div(other: number | Wide): Wide;
  pow(power: bigint | number): Wide;
  cmp(other: number | Wide): -1 | 0 | 1;
  toNumber(): number | null;
  toJSON(): WideJSON;
}
export function wideExp(value: number): Wide;

export const EXTENDED_ALGORITHM: 'vixen-hyperfield-c2/6';
export const EXTENDED_TAIL_ALGORITHM: 'vixen-tail-c2/4';
export const MAX_DOMAIN_END: number;
export interface ExtendedConfig extends Config { readonly domainEnd?: number; }
export interface ExtendedSnapshot {
 readonly algorithm: typeof EXTENDED_ALGORITHM;
 readonly config: Config;
 readonly domainEnd: number;
 readonly lineage: readonly ForkOptions[];
}
export interface ExtendedTailSnapshot {
 readonly algorithm: typeof EXTENDED_TAIL_ALGORITHM;
 readonly config: Config;
 readonly domainEnd: number;
 readonly from: number;
 readonly seed: string | number;
 readonly leafSeed: number;
 readonly foldedCount: number;
 readonly witness: string;
 readonly lineage: readonly ForkOptions[];
}
export type ScaledSample = Readonly<Omit<NormalizedSample,'deviation'|'guideDeviation'|'unconstrainedDeviation'|'unconstrainedGuideDeviation'|'multiplier'|'logBase'> & {
 deviation: number | null;
 guideDeviation: number | null;
 unconstrainedDeviation: number | null;
 unconstrainedGuideDeviation: number | null;
 multiplier: number | null;
 logBase: number | null;
 scaledValue: WideJSON;
 scaledBase: WideJSON;
 scaledGuideValue: WideJSON;
 scaledUnconstrainedValue: WideJSON;
 scaledUnconstrainedGuideValue: WideJSON;
 scaledPositiveValue: WideJSON;
 scaledFloorValue: WideJSON | null;
 scaledCeilingValue: WideJSON | null;
 scaledBoundaryWidth: WideJSON;
 scaledDeviation: WideJSON;
 scaledGuideDeviation: WideJSON;
 scaledUnconstrainedDeviation: WideJSON;
 scaledUnconstrainedGuideDeviation: WideJSON;
}>;
export interface ExtendedInstrument {
 readonly algorithm: typeof EXTENDED_ALGORITHM | typeof EXTENDED_TAIL_ALGORITHM;
 readonly config: ResolvedConfig;
 readonly domain: Pair;
 readonly normalizedDomain: Pair;
 readonly lineage: readonly ForkEvent[];
 sample(x: number): ScaledSample;
 at(x: number, coordinates: Vector): ScaledSample;
 sampleNormalized(x: number): ScaledSample;
 atNormalized(x: number, coordinates: Vector): ScaledSample;
 location(x: number): readonly number[];
 inspect(x: number): readonly Contribution[];
 fork(options: ForkOptions): ExtendedInstrument;
 snapshot(): ExtendedSnapshot | ExtendedTailSnapshot;
 tail(from: number): ExtendedTailSnapshot;
}
export function createExtended(options?: ExtendedConfig): ExtendedInstrument;
export function restoreExtended(snapshot: ExtendedSnapshot | ExtendedTailSnapshot): ExtendedInstrument;

export const EXTENDED_OPERATION_ALGORITHM: 'vixen-extended-operations/1';
export const EXTENDED_COUPLING_ALGORITHM: 'vixen-extended-coupling/1';
export interface ExtendedPolynomialGuideSpec {
 readonly format: 'vixen-extended-guide/1'; readonly kind: 'polynomial';
 readonly coefficients: readonly number[]; readonly center: number; readonly scale: number; readonly domain: Pair;
}
export type ExtendedGuideSpec = Guide | ExtendedPolynomialGuideSpec;
export interface ExtendedOperationSnapshot {
 readonly algorithm: typeof EXTENDED_OPERATION_ALGORITHM;
 readonly operation: 'influence'|'impulse'|'redistribution'; readonly curve: Snapshot;
 readonly recipe: Readonly<Record<string,unknown>>;
}
export interface ExtendedCouplingSnapshot {
 readonly algorithm: typeof EXTENDED_COUPLING_ALGORITHM;
 readonly operation: 'group'; readonly curves: readonly Snapshot[]; readonly strength: number; readonly window: Pair;
}
export interface ExtendedCouplingViewSnapshot {
 readonly algorithm: typeof EXTENDED_COUPLING_ALGORITHM;
 readonly operation: 'view'; readonly group: Snapshot; readonly index: number;
}
export interface ExtendedComposition extends Omit<ExtendedInstrument,'algorithm'|'fork'|'snapshot'|'tail'> {
 readonly algorithm: typeof EXTENDED_OPERATION_ALGORITHM;
 influence(target: ExtendedGuideSpec, options?: Omit<InfluenceOptions,'count'>): ExtendedComposition;
 impulse(recipe: ImpulseOptions): ExtendedComposition;
 redistribute(window: Pair, shape: RedistributionShape): ExtendedComposition;
 fork(options: ForkOptions): ExtendedComposition;
 snapshot(): Snapshot;
 tail(from: number): Snapshot;
}
export type ExtendedSource = ExtendedInstrument | ExtendedComposition;
export interface ExtendedCoupledGroup {
 readonly algorithm: typeof EXTENDED_COUPLING_ALGORITHM;
 readonly domain: Pair; readonly curves: readonly ExtendedComposition[];
 sampleNormalized(x: number): readonly ScaledSample[];
 atNormalized(x: number, coordinates: Vector): readonly ScaledSample[];
 sample(x: number): readonly ScaledSample[];
 at(x: number, coordinates: Vector): readonly ScaledSample[];
 snapshot(): Snapshot; tail(from: number): Snapshot;
}
export function extendedPolynomialGuide(coefficients: readonly number[], options?: {center?:number;scale?:number;domain?:Pair}): ExtendedPolynomialGuideSpec;
export function withExtendedInfluence(curve: ExtendedSource,target: ExtendedGuideSpec,options?:Omit<InfluenceOptions,'count'>): ExtendedComposition;
export function withExtendedImpulse(curve: ExtendedSource,recipe: ImpulseOptions): ExtendedComposition;
export function withExtendedRedistribution(curve: ExtendedSource,recipe: Readonly<{from:number;until:number;coefficients:readonly number[];magnitude?:number;measure?:'actualDeviation'|'unconstrainedDeviation'}>): ExtendedComposition;
export function coupleExtended(curves: readonly ExtendedSource[],strength:number,window:Pair): ExtendedCoupledGroup;
