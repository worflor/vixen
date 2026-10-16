import math
import unittest
from decimal import Decimal, localcontext
from types import SimpleNamespace

from vixen import Vixen
from vixen_interval import (interval, add, subtract, multiply, divide, hull,
                            smooth5, boundaryRamp, tanh, exp,
                            nativeDeviationEnclosure)

class IntervalTests(unittest.TestCase):
    def contains(self, q, x):
        self.assertLessEqual(q.lo, x)
        self.assertLessEqual(x, q.hi)

    def test_arithmetic_edges(self):
        self.contains(add(interval(1, 2), interval(3, 4)), 4)
        self.contains(subtract(interval(1, 2), interval(3, 4)), -2)
        self.contains(multiply(interval(-2, 3), interval(-4, 5)), -12)
        self.contains(multiply(interval(0), interval(1e-300)), 0)
        self.contains(divide(interval(1), interval(3)), 1/3)
        self.assertEqual(hull(interval(-1, 0), interval(2, 3)), interval(-1, 3))
        with self.assertRaises(ZeroDivisionError): divide(interval(1), interval(-1, 1))
        with self.assertRaises(OverflowError): multiply(interval(float.fromhex('0x1.fffffffffffffp+1023')), interval(2))

    def test_polynomials_transcendentals(self):
        self.contains(smooth5(interval(.5)), .5)
        self.assertLessEqual(smooth5(interval(-2, 2)).lo, 0)
        self.assertGreaterEqual(smooth5(interval(-2, 2)).hi, 1)
        self.contains(boundaryRamp(interval(1.5)), 1.5)
        self.contains(tanh(interval(-1000, 1000)), 0)
        self.assertEqual(tanh(interval(1000)).hi, 1)
        tiny=exp(interval(-10000))
        self.assertEqual(tiny.lo, 0)
        self.assertLessEqual(tiny.hi, math.ulp(0.0))
        with self.assertRaises(OverflowError): exp(interval(1000))
        for x in (-20.0, -1.0, -0.25, 0.25, 1.0, 20.0):
            q=exp(interval(x))
            with localcontext() as ctx:
                ctx.prec=100
                ref=Decimal.from_float(x).exp()
                self.assertLessEqual(Decimal.from_float(q.lo), ref)
                self.assertGreaterEqual(Decimal.from_float(q.hi), ref)

    def test_native_leaf_dense_records(self):
        def facade(curve):
            return SimpleNamespace(config=curve.config,dips=curve.dips,domain=curve.domain,
                                   sample=curve.sample,at=curve.at,ridges=curve.ridges)
        h=tuple((i-7)*.17 for i in range(16))
        for floor in ('none','zero','baseline','backbone'):
            curve=Vixen(seed='interval-seeded-dense',openingEndpoint=74,introVariation=.6,
                        floor=floor,ceilingMargin=None if floor=='none' else 1.25,
                        boundarySoftness=.37,stabilizer=.41,strength=1.2,contrast=7.5)
            leaf=facade(curve)
            for i in range(97):
                x=74+i*.43
                q=nativeDeviationEnclosure(leaf,x,x)
                self.contains(q,curve.sample(x)['deviation'])
                self.assertLess(q.hi-q.lo,1e-7)
                self.contains(nativeDeviationEnclosure(leaf,x,x,h),curve.at(x,h)['deviation'])
                threshold=curve.sample(x)['deviation']
                self.assertLessEqual(q.lo,threshold)
                self.assertGreaterEqual(q.hi,threshold)
            self.contains(nativeDeviationEnclosure(leaf,73,74),0)
            with self.assertRaises(ValueError): nativeDeviationEnclosure(leaf,0,1)

    def test_opening_and_bad_coordinates(self):
        curve=Vixen(seed=23,openingEndpoint=20)
        leaf=SimpleNamespace(config=curve.config,dips=curve.dips,domain=curve.domain,
                             sample=curve.sample,at=curve.at,ridges=curve.ridges)
        self.assertEqual(nativeDeviationEnclosure(leaf,19,20),interval(0))
        self.contains(nativeDeviationEnclosure(leaf,19,21),0)
        with self.assertRaises(ValueError): nativeDeviationEnclosure(leaf,20,21,[1,2])

if __name__=='__main__':
    unittest.main()
