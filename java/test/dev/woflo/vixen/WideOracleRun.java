package dev.woflo.vixen;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class WideOracleRun {
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception{
        Map<String,Object> data=(Map<String,Object>)VixenJson.parse(Files.readString(Path.of("tests/fixtures/scaled-binary64-1/rounding.json")));
        BigInteger scale=new BigInteger((String)data.get("commonScale"));List<Object> cases=(List<Object>)data.get("cases");int count=0;
        for(Object value:cases){
            Map<String,Object> row=(Map<String,Object>)value;Wide a=Wide.fromDouble(((Number)row.get("a")).doubleValue()).scale(scale),b=Wide.fromDouble(((Number)row.get("b")).doubleValue()).scale(scale);
            String op=(String)row.get("op");Wide result=switch(op){case "add"->a.add(b).scale(scale.negate());case "mul"->a.mul(b).scale(scale.shiftLeft(1).negate());case "div"->a.div(b);default->throw new AssertionError("unknown oracle operation");};
            Double rounded=result.toDouble();if(rounded==null)throw new AssertionError("representable oracle became unavailable");
            String bits=String.format("%016x",Double.doubleToRawLongBits(rounded));
            if(!bits.equals(row.get("expectedBits")))throw new AssertionError(op+" rounding "+bits+" != "+row.get("expectedBits"));count++;
        }
        if(count!=768)throw new AssertionError("incomplete independent oracle corpus");
        BigInteger huge=BigInteger.ONE.shiftLeft(20000);Wide large=new Wide(BigInteger.ONE,huge);
        if(large.toDouble()!=null||large.cmp(Wide.fromMap(large.toMap()))!=0||large.add(large.neg()).sign()!=0)throw new AssertionError("giant exponent handling");
        for(String bad:new String[]{"-0","+1","01","1\n"," 1","1 ","١"}){
            try{Wide.fromMap(Map.of("format",Wide.FORMAT,"sign",1,"significand",1.0,"exponent2",bad));throw new AssertionError("malformed exponent accepted");}catch(IllegalArgumentException expected){}
        }
        if(Wide.fromRatio(BigInteger.ONE,BigInteger.valueOf(3)).toDouble()!=1.0/3)throw new AssertionError("exact ratio construction");
        if(WideExponential.exp(0).toDouble()!=1.0||WideExponential.exp(1e308).toDouble()!=null||WideExponential.exp(-1e308).toDouble()!=null)throw new AssertionError("exponential range reduction");
        Map<String,Object> expData=(Map<String,Object>)VixenJson.parse(Files.readString(Path.of("tests/fixtures/scaled-binary64-1/exponential.json")));
        List<Object> expCases=(List<Object>)expData.get("cases");if(expCases.size()!=21)throw new AssertionError("incomplete exponential corpus");
        for(Object value:expCases){Map<String,Object> row=(Map<String,Object>)value;Wide q=WideExponential.exp(((Number)row.get("x")).doubleValue());BigInteger e=new BigInteger((String)row.get("e")),m=new BigInteger((String)row.get("m")),gap=q.e().subtract(e);if(gap.abs().compareTo(BigInteger.ONE)>0)throw new AssertionError("exponential exponent mismatch");int shift=gap.intValueExact();if(q.m().shiftLeft(Math.max(0,shift)).subtract(m.shiftLeft(Math.max(0,-shift))).abs().compareTo(BigInteger.valueOf(8))>0)throw new AssertionError("exponential significand mismatch");}
        System.out.println("Java Wide: "+count+" independent Fraction rounding cases, giant exponent and malformed encodings passed");
    }
}
