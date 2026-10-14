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
  readonly algorithm: typeof ALGORITHM;
  readonly config: ResolvedConfig;
  readonly bounds: Bounds;
  readonly domain: readonly [number, number];
  readonly dips: readonly Readonly<{center:number;width:number;depth:number}>[];
  readonly lineage: readonly (ForkOptions & {readonly seed:number})[];
  fork(options: ForkOptions): Instrument;
  snapshot(): Snapshot;
  sample(x: number): Sample;
  at(x: number, coordinates: Vector): Sample;
  location(x: number): readonly number[];
  inspect(x: number): readonly Contribution[];
  ridges(): readonly Ridge[];
}
export interface ForkOptions { readonly at:number; readonly operation:string; readonly handoffWidth?:number; }
export interface Snapshot { readonly algorithm: string; readonly config: Config; readonly lineage?:readonly ForkOptions[]; }
export function create(options?: Config): Instrument;
export function restore(snapshot: Snapshot): Instrument;
export function seed32(seed: string | number): number;
export function hash32(seed: number, index: number, channel: number): number;
export function smooth5(t: number): number;
export function bump(t: number): number;
export function baseline(x: number): number;
export function navigationBasis(index?: number): Readonly<{u: readonly number[]; v: readonly number[]}>;

export function boundaryRamp(t: number): number;
