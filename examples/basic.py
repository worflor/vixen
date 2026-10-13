from vixen import Vixen
import json
curve=Vixen(seed='my-world',stabilizer=0.85)
print(json.dumps(curve.sample(144),indent=2))
print('Activity at level 300:',curve.sample(300)['activity'])
