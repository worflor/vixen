package dev.woflo.vixen;

import java.math.BigInteger;

final class Floating {
 private Floating(){}
 private record Dyadic(BigInteger n,int exponent){}
 private static Dyadic dyadic(double value){
  if(!Double.isFinite(value))throw new IllegalArgumentException("Finite binary64 operand required");
  long bits=Double.doubleToRawLongBits(value);int encoded=(int)((bits>>>52)&2047);
  long mantissa=(bits&0xfffffffffffffL)|(encoded==0?0L:1L<<52);
  BigInteger n=BigInteger.valueOf(mantissa);
  return new Dyadic(bits<0?n.negate():n,encoded==0?-1074:encoded-1075);
 }
 private static Dyadic sum(Dyadic[] terms){
  int exponent=Integer.MAX_VALUE;
  for(Dyadic t:terms)if(t.n.signum()!=0)exponent=StrictMath.min(exponent,t.exponent);
  if(exponent==Integer.MAX_VALUE)return new Dyadic(BigInteger.ZERO,0);
  BigInteger n=BigInteger.ZERO;
  for(Dyadic t:terms)if(t.n.signum()!=0)n=n.add(t.n.shiftLeft(t.exponent-exponent));
  return new Dyadic(n,exponent);
 }
 private static double rounded(Dyadic value,int denominator){
  if(value.n.signum()==0)return 0;
  boolean negative=value.n.signum()<0;
  BigInteger n=value.n.abs(),d=BigInteger.valueOf(denominator);
  int k=n.bitLength()-d.bitLength();
  if((k>=0?n.compareTo(d.shiftLeft(k)):n.shiftLeft(-k).compareTo(d))<0)k--;
  int shift=StrictMath.max(k+value.exponent-52,-1074),scale=value.exponent-shift;
  if(scale>=0)n=n.shiftLeft(scale);else d=d.shiftLeft(-scale);
  BigInteger[] qr=n.divideAndRemainder(d);int comparison=qr[1].shiftLeft(1).compareTo(d);
  if(comparison>0||(comparison==0&&qr[0].testBit(0)))qr[0]=qr[0].add(BigInteger.ONE);
  double result=StrictMath.scalb(qr[0].doubleValue(),shift);
  return negative?-result:result;
 }
 static double mean(double[] values){
  if(values.length==0)throw new IllegalArgumentException("Nonempty numeric array required");
  Dyadic[] terms=new Dyadic[values.length];boolean same=true;
  for(int i=0;i<values.length;i++){
   terms[i]=dyadic(values[i]);same&=Double.doubleToRawLongBits(values[i])==Double.doubleToRawLongBits(values[0]);
  }
  return same?values[0]:rounded(sum(terms),values.length);
 }
 static double sumProducts(double[][] products){
  Dyadic[] terms=new Dyadic[products.length];
  for(int i=0;i<products.length;i++){
   BigInteger n=BigInteger.ONE;int exponent=0;
   for(double factor:products[i]){Dyadic f=dyadic(factor);n=n.multiply(f.n);exponent+=f.exponent;}
   terms[i]=new Dyadic(n,exponent);
  }
  return rounded(sum(terms),1);
 }
}
