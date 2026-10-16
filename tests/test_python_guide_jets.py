import unittest
from vixen_guide import polynomial_guide,taylor_guide,sampled_guide,capture_guide,compile_guide,guide_jet

class GuideJetTests(unittest.TestCase):
    def test_polynomial_units_and_taylor(self):
        g=compile_guide(polynomial_guide([3,2,-.25,.5],center=150,scale=10))
        j=guide_jet(g,153);z=.3
        self.assertEqual(j.value,g.evaluate(153))
        self.assertAlmostEqual(j.d1,(2-.5*z+1.5*z*z)/10,places=13)
        self.assertAlmostEqual(j.d2,(-.5+3*z)/100,places=13)
        q=guide_jet(taylor_guide([7,3,4],center=150),150)
        self.assertEqual((q.value,q.d1,q.d2),(7,3,4))
    def test_actual_quintic_and_capture_interpolants(self):
        s=sampled_guide([dict(x=120,y=0,d1=0,d2=0),dict(x=160,y=64000,d1=4800,d2=240)])
        for x,y,d1,d2 in [(120,0,0,0),(135,3375,675,90),(160,64000,4800,240)]:
            q=guide_jet(s,x);self.assertEqual((q.value,q.d1,q.d2),(y,d1,d2))
        c=capture_guide(lambda x:x*x,domain=[120,160],count=3,derivative=lambda x:2*x,curvature=lambda x:2)
        q=guide_jet(c,135);self.assertEqual((q.value,q.d1,q.d2),(18225,270,2))
        with self.assertRaises(ValueError):guide_jet(s,119)
