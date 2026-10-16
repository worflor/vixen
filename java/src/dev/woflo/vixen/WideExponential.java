package dev.woflo.vixen;

import java.math.BigInteger;

public final class WideExponential {
    private WideExponential(){}
    private static final BigInteger LN2=new BigInteger("22400441642467859577488739781209226459403626065886660720528601240145527492420725336291667021425889702299778972254785424771724351736870381509034525077594234322856729913233103191693932917751924172625994619620400660039442608528415575721354420972082577163443760602448842212312094033841292568983687855542170368247819631661533385799671122421173221385317065110553753226746392818491381768398893626906223910200995167915538026273569853823698607422800781837962733502154433664101279894325772997090164915246519231150831430415653842396495805010817059154674077638814850832366084056018734798876353421653877927763165084799176609677707");

    private static BigInteger nearest(BigInteger n,BigInteger d){
        BigInteger[] qr=n.divideAndRemainder(d);int side=qr[1].shiftLeft(1).compareTo(d);
        return side>0||side==0&&qr[0].testBit(0)?qr[0].add(BigInteger.ONE):qr[0];
    }
    public static Wide exp(double value){
        if(!Double.isFinite(value))throw new IllegalArgumentException("Finite binary64 exponential argument required");
        Wide x=Wide.fromDouble(value);int e=x.e().intValueExact();
        BigInteger numerator=e>=0?x.m().shiftLeft(e):x.m(),denominator=e>=0?BigInteger.ONE:BigInteger.ONE.shiftLeft(-e);
        BigInteger k=nearest(numerator.abs().shiftLeft(2048),denominator.multiply(LN2));if(numerator.signum()<0)k=k.negate();
        Wide residual=Wide.fromRatio(numerator.shiftLeft(2048).subtract(k.multiply(LN2).multiply(denominator)),denominator.shiftLeft(2048));
        Double r=residual.toDouble();
        if(r==null){if(residual.e().add(BigInteger.valueOf(52)).compareTo(BigInteger.valueOf(-1075))>0)throw new ArithmeticException("Unrepresentable exponential residual");r=0.0;}
        if(Math.abs(r)>.36)throw new ArithmeticException("Exponential range reduction failed");
        return Wide.fromDouble(StrictMath.exp(r)).scale(k);
    }
}
