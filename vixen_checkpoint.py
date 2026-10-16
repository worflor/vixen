"""Canonical history commitments for bounded checkpoint exports."""
import hashlib
import json
import struct

def history_witness(prior, entries):
    rows = [[struct.pack('>d', e['at']).hex(), e['operation'], struct.pack('>d', e['handoffWidth']).hex()] for e in entries]
    text = json.dumps(['vixen-history/1', prior, rows], ensure_ascii=True, separators=(',', ':'))
    return hashlib.sha256(text.encode('ascii')).hexdigest()
