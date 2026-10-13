import {create,restore,type Config,type Sample} from '@worflor/vixen';
const options:Config={seed:'typed-world',stabilizer:0.73123,velocity:new Float64Array(16)};
const field=create(options);const sample:Sample=field.sample(233.125);
const replay=restore({algorithm:field.algorithm,config:field.config});
console.log(sample.activity,replay.sample(233.125).value);
