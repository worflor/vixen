import {create,restore,type Config,type Sample} from '@worflor/vixen';
const options:Config={seed:'typed-world',stabilizer:0.73123,velocity:new Float64Array(16)};
const field=create(options);const sample:Sample=field.sample(233.125);
const branch=field.fork({at:200,operation:'typed-door',handoffWidth:24});
const replay=restore(branch.snapshot());
console.log(branch.lineage[0]?.seed,field.domain,field.dips);
console.log(sample.activity,replay.sample(233.125).value);
