import {Wide} from './vixen-wide.mjs';
const LN2=22400441642467859577488739781209226459403626065886660720528601240145527492420725336291667021425889702299778972254785424771724351736870381509034525077594234322856729913233103191693932917751924172625994619620400660039442608528415575721354420972082577163443760602448842212312094033841292568983687855542170368247819631661533385799671122421173221385317065110553753226746392818491381768398893626906223910200995167915538026273569853823698607422800781837962733502154433664101279894325772997090164915246519231150831430415653842396495805010817059154674077638814850832366084056018734798876353421653877927763165084799176609677707n,BITS=2048n;

function nearest(n,d){let q=n/d,r=n%d;if(2n*r>d||(2n*r===d&&(q&1n)))q++;return q;}
export function wideExp(value){
 if(typeof value!=='number'||!Number.isFinite(value))throw new TypeError('A finite binary64 exponential argument is required');
 const x=Wide.fromNumber(value),numerator=x.e>=0n?x.m<<x.e:x.m,denominator=x.e>=0n?1n:1n<<-x.e;
 const magnitude=numerator<0n?-numerator:numerator;
 let k=nearest(magnitude<<BITS,denominator*LN2);if(numerator<0n)k=-k;
 const residual=Wide.fromRatio((numerator<<BITS)-k*LN2*denominator,denominator<<BITS);
 let r=residual.toNumber();
 if(r===null){if(residual.e+52n>-1075n)throw new RangeError('Unrepresentable exponential residual');r=0;}
 if(Math.abs(r)>.36)throw new RangeError('Exponential range reduction failed');
 return Wide.fromNumber(Math.exp(r)).scale(k);
}
