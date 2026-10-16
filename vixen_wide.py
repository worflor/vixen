"""Finite 53-bit significands with explicit unbounded integer binary exponents."""
import math,re
from collections.abc import Mapping

WIDE_FORMAT='vixen-scaled-binary64/1'

def _exponent(value):
    if isinstance(value,int) and not isinstance(value,bool):return value
    if isinstance(value,float) and math.isfinite(value) and value.is_integer() and abs(value)<=2**53-1:return int(value)
    raise TypeError('An exact integer exponent is required')

def _round_ratio(n,d):
    q,r=divmod(n,d)
    if 2*r>d or (2*r==d and q&1):q+=1
    return q

def _normalized(m,e):
    if not m:return 0,0
    negative=m<0;n=abs(m);shift=n.bit_length()-53
    if shift>0:n=_round_ratio(n,1<<shift);e+=shift
    elif shift<0:n<<=-shift;e+=shift
    if n==1<<53:n>>=1;e+=1
    return (-n if negative else n),e

def _decimal(value):
    if not value:return '0'
    negative=value<0;value=abs(value);parts=[]
    while value:
        value,part=divmod(value,10**9);parts.append(part)
    text=str(parts[-1])+''.join(str(p).zfill(9) for p in reversed(parts[:-1]))
    return '-'+text if negative else text

def _parse_decimal(text):
    negative=text.startswith('-');digits=text[1:] if negative else text;n=0
    for i in range(0,len(digits),9):
        block=digits[i:i+9];n=n*10**len(block)+int(block)
    return -n if negative else n

class Wide:
    __slots__=('m','e')
    def __init__(self,m,e=0):
        if isinstance(m,bool) or not isinstance(m,int):raise TypeError('An integer significand is required')
        m,e=_normalized(m,_exponent(e))
        object.__setattr__(self,'m',m);object.__setattr__(self,'e',e)
    def __setattr__(self,name,value):raise AttributeError('Scaled numbers are immutable')
    @staticmethod
    def from_number(value):
        if isinstance(value,bool) or not isinstance(value,(int,float)):raise TypeError('Finite binary64 input required')
        try:value=float(value)
        except OverflowError:raise ValueError('Finite binary64 input required') from None
        if not math.isfinite(value):raise ValueError('Finite binary64 input required')
        n,d=value.as_integer_ratio();return Wide(n,-(d.bit_length()-1))
    @staticmethod
    def from_ratio(n,d,power=0):
        if isinstance(n,bool) or isinstance(d,bool) or not isinstance(n,int) or not isinstance(d,int):raise TypeError('Exact integer numerator and denominator required')
        if d==0:raise ValueError('Nonzero denominator required')
        e=_exponent(power)
        if n==0:return Wide(0)
        negative=(n<0)!=(d<0);n,d=abs(n),abs(d);p=n.bit_length()-d.bit_length()
        if (n<(d<<p) if p>=0 else (n<<-p)<d):p-=1
        shift=52-p;q=_round_ratio(n<<shift,d) if shift>=0 else _round_ratio(n,d<<-shift)
        return Wide(-q if negative else q,e+p-52)
    @staticmethod
    def of(value):return value if isinstance(value,Wide) else Wide.from_number(value)
    @property
    def sign(self):return -1 if self.m<0 else 1 if self.m>0 else 0
    def neg(self):return Wide(-self.m,self.e)
    def abs(self):return self.neg() if self.m<0 else self
    def scale(self,power):return Wide(self.m,self.e+_exponent(power))
    def add(self,other):
        b=Wide.of(other)
        if not self.m:return b
        if not b.m:return self
        a,c=(self,b) if self.e>=b.e else (b,self);d=a.e-c.e
        if d>54:return a
        return Wide((a.m<<d)+c.m,c.e)
    def sub(self,other):return self.add(Wide.of(other).neg())
    def mul(self,other):
        b=Wide.of(other);return Wide(self.m*b.m,self.e+b.e)
    def div(self,other):
        b=Wide.of(other)
        if not b.m:raise ValueError('Nonzero divisor required')
        if not self.m:return self
        n,d=abs(self.m),abs(b.m);s=53 if n<d else 52
        q=_round_ratio(n<<s,d)
        return Wide(-q if (self.m<0)!=(b.m<0) else q,self.e-b.e-s)
    def pow(self,power):
        n=_exponent(power)
        if n<0:return Wide.from_number(1).div(self.pow(-n))
        base,out=self,Wide.from_number(1)
        while n:
            if n&1:out=out.mul(base)
            n>>=1
            if n:base=base.mul(base)
        return out
    def cmp(self,other):
        b=Wide.of(other)
        if self.sign!=b.sign:return 1 if self.sign>b.sign else -1
        if not self.m:return 0
        if self.e!=b.e:return (1 if self.e>b.e else -1)*self.sign
        return 1 if self.m>b.m else -1 if self.m<b.m else 0
    def to_number(self):
        if not self.m:return 0.0
        n,p=abs(self.m),self.e+52
        if p>1023:return None
        if p>=-1022:return self.sign*math.ldexp(n/(2**52),p)
        shift=-1074-self.e
        if shift>53:return None
        q=_round_ratio(n,1<<shift)
        return self.sign*q*float.fromhex('0x0.0000000000001p-1022') if q else None
    def to_json(self):
        return dict(format=WIDE_FORMAT,sign=self.sign,significand=abs(self.m)/(2**52) if self.m else 0,exponent2=_decimal(self.e+52) if self.m else '0')
    @staticmethod
    def decode(value):
        if not isinstance(value,Mapping) or set(value)!={'format','sign','significand','exponent2'}:raise TypeError('Invalid scaled number')
        sign,s,e=value['sign'],value['significand'],value['exponent2']
        if value['format']!=WIDE_FORMAT or isinstance(sign,bool) or sign not in (-1,0,1) or not isinstance(e,str) or not re.fullmatch(r'(?:0|-?[1-9][0-9]*)',e):raise ValueError('Invalid scaled number')
        if not sign:
            if s!=0 or isinstance(s,bool) or e!='0':raise ValueError('Noncanonical scaled zero')
            return Wide.from_number(0)
        if isinstance(s,bool) or not isinstance(s,(int,float)) or not math.isfinite(s) or not 1<=s<2:raise ValueError('Invalid scaled significand')
        return Wide.from_number(sign*s).scale(_parse_decimal(e))
