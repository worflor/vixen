import {create} from '@worflor/vixen';

const bounded=create({seed:'my-world',floor:'backbone',ceilingMargin:.35,boundarySoftness:.1,stabilizer:.3});
for(const x of [50,100,144,200,300]){
 const p=bounded.sample(x);
 console.log(JSON.stringify({x,source:p.unconstrainedValue,value:p.value,floor:p.floorValue,ceiling:p.ceilingValue,changed:p.constraintApplied}));
}
