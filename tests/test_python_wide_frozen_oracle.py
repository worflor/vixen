import json,struct,unittest
from pathlib import Path
from vixen_wide import Wide,WIDE_FORMAT

class FrozenWideOracleTests(unittest.TestCase):
    def test_complete_frozen_fraction_corpus(self):
        data=json.loads((Path(__file__).parent/'fixtures/scaled-binary64-1/rounding.json').read_text());self.assertEqual(data['format'],WIDE_FORMAT);self.assertEqual(len(data['cases']),768)
        scale=int(data['commonScale'])
        for c in data['cases']:
            a,b=Wide.from_number(c['a']).scale(scale),Wide.from_number(c['b']).scale(scale);r=getattr(a,c['op'])(b)
            if c['op']=='add':r=r.scale(-scale)
            elif c['op']=='mul':r=r.scale(-2*scale)
            self.assertIsNotNone(r.to_number());self.assertEqual(struct.pack('>d',r.to_number()).hex(),c['expectedBits'])
    def test_malformed_exponents(self):
        for text in ['-0','+1','01','1\n',' 1','1 ','١']:
            with self.assertRaises((TypeError,ValueError)):Wide.decode(dict(format=WIDE_FORMAT,sign=1,significand=1,exponent2=text))
