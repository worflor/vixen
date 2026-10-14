export const ALGORITHM: 'vixen-hyperfield-c2/4';
export const DIMENSIONS: 16;
export const FEATURES: 48;
export type Vector = readonly number[] | Int8Array | Uint8Array | Uint8ClampedArray | Int16Array | Uint16Array | Int32Array | Uint32Array | Float32Array | Float64Array;
export type Floor = 'none' | 'zero' | 'baseline' | 'backbone';
export interface Config {
  openingEndpoint?: number;
  introVariation?: number;
  floor?: Floor;
  ceilingMargin?: number | null;
  boundarySoftness?: number;
  seed?: string | number;
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
  readonly seed: string | number;
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
  readonly sourceSeed?: string | number;
  readonly index: number;
  readonly scale: number;
  readonly coordinate: number;
  readonly cell: number;
  readonly contribution: number;
  readonly guideContribution: number;
}
export interface Bounds {
  readonly unconstrainedSpatialSlope: number;
  readonly score: readonly [number, number];
  readonly deviation: readonly [number, number];
  readonly multiplier: readonly [number, number];
  readonly activity: readonly [number, number];
  readonly positiveMultiplier: readonly [number, number];
}
export interface Instrument {
  readonly algorithm: typeof ALGORITHM | typeof INFLUENCE_ALGORITHM;
  readonly config: ResolvedConfig;
  readonly bounds: Bounds;
  readonly domain: readonly [number, number];
  readonly dips: readonly Readonly<{center:number;width:number;depth:number}>[];
  readonly lineage: readonly (ForkOptions & {readonly seed:number})[];
  readonly influences?:readonly InfluenceEntry[];
  influence(target:Guide|((x:number)=>number),options?:InfluenceOptions):Instrument;
  influenceAt?(x:number):Readonly<{weight:number;targetValue:number|null;sourceValue:number;followingError:number|null}>;
  fork(options: ForkOptions): Instrument;
  snapshot(): Snapshot;
  sample(x: number): Sample;
  at(x: number, coordinates: Vector): Sample;
  location(x: number): readonly number[];
  inspect(x: number): readonly Contribution[];
  ridges(): readonly Ridge[];
}
export interface ForkOptions { readonly at:number; readonly operation:string; readonly handoffWidth?:number; }
export interface BaseSnapshot { readonly algorithm: string; readonly config: Config; readonly lineage?:readonly ForkOptions[]; }
export type Snapshot=BaseSnapshot|InfluenceSnapshot;
export const INFLUENCE_ALGORITHM: 'vixen-influence-c2/1';
export interface PolynomialGuide {readonly format:'vixen-guide/1';readonly kind:'polynomial';readonly coefficients:readonly number[];readonly center:number;readonly scale:number;readonly domain:readonly [number,number];}
export interface GuideJet {readonly x:number;readonly y:number;readonly d1:number;readonly d2:number;}
export interface SampledGuide {readonly format:'vixen-guide/1';readonly kind:'quintic';readonly knots:readonly GuideJet[];}
export type Guide=PolynomialGuide|SampledGuide;
export interface InfluenceOptions {readonly at?:number;readonly amount?:number;readonly handoffWidth?:number;readonly count?:number;}
export interface InfluenceEntry {readonly at:number;readonly amount:number;readonly handoffWidth:number;readonly guide:Guide;}
export interface InfluenceSnapshot extends InfluenceEntry {readonly algorithm:typeof INFLUENCE_ALGORITHM;readonly curve:Snapshot;}
export interface CompiledGuide {readonly spec:Guide;readonly domain:readonly [number,number];evaluate(x:number):number;}
export function polynomialGuide(coefficients:readonly number[],options?:{center?:number;scale?:number;domain?:readonly [number,number]}):PolynomialGuide;
export function taylorGuide(derivatives:readonly number[],options?:{center?:number;domain?:readonly [number,number]}):PolynomialGuide;
export function sampledGuide(knots:readonly GuideJet[]):SampledGuide;
export function captureGuide(fn:(x:number)=>number,options?:{domain?:readonly [number,number];count?:number;derivative?:(x:number)=>number;curvature?:(x:number)=>number}):SampledGuide;
export function compileGuide(guide:Guide):CompiledGuide;
export function evaluateGuide(guide:Guide,x:number):number;

export function create(options?: Config): Instrument;
export function restore(snapshot: Snapshot): Instrument;
export function seed32(seed: string | number): number;
export function hash32(seed: number, index: number, channel: number): number;
export function smooth5(t: number): number;
export function bump(t: number): number;
export function baseline(x: number): number;
export function navigationBasis(index?: number): Readonly<{u: readonly number[]; v: readonly number[]}>;

export function boundaryRamp(t: number): number;
