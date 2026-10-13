import {create} from '@worflor/vixen';
const curve=create({seed:'my-world',stabilizer:0.85});
console.log(JSON.stringify(curve.sample(144),null,2));
console.log('Activity at level 300:',curve.sample(300).activity);
