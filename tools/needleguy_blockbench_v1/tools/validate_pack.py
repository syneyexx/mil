"""Structural, UV, hierarchy, texture and transformed-geometry checks."""
from pathlib import Path
import json, base64, io, sys
from PIL import Image
import numpy as np
from asset_core import vertices, ROOT

def main():
 manifest=json.loads((ROOT/'manifest.json').read_text());assert len(manifest)==1
 report=[]
 for entry in manifest:
  p=ROOT/entry['model'];m=json.loads(p.read_text());els=m['elements'];ids={e['uuid'] for e in els};assert len(ids)==len(els),(p,'duplicate cube IDs')
  seen=[];groups=[];names=[]
  def walk(n):
   if isinstance(n,str):seen.append(n);return
   assert len(n['origin'])==3;assert n['rotation']==[0,0,0]
   groups.append(n['uuid']);names.append(n['name'])
   for child in n['children']:walk(child)
  for node in m['outliner']:walk(node)
  assert set(seen)==ids and len(seen)==len(ids),(p,'missing/duplicate cube references')
  assert len(set(groups))==len(groups) and not set(groups)&ids
  assert len(set(names))==len(names)
  assert m['meta']['model_format']=='free'
  assert m['resolution']=={'width':256,'height':256}
  external=(ROOT/entry['texture']).read_bytes();embedded=base64.b64decode(m['textures'][0]['source'].split(',')[1]);assert embedded==external
  im=Image.open(io.BytesIO(external));assert im.size==(256,256) and im.mode=='RGBA'
  for e in els:
   assert all(a<b for a,b in zip(e['from'],e['to'])),(p,e['name'],'invalid cube')
   assert set(e['faces'])=={'north','south','east','west','up','down'}
   for f in e['faces'].values():
    assert f['texture']==0
    u,v,s,t=f['uv'];assert 0<=u<s<=256 and 0<=v<t<=256
   assert all(np.isfinite(x) for x in e['from']+e['to']+e['origin']+e['rotation'])
  points=np.concatenate([vertices(e) for e in els]);low,high=points.min(0),points.max(0)
  assert abs(low[1])<1e-4,(p,'feet not y=0',low[1])
  assert abs(high[1]-40)<1e-5,(p,'not exactly 2.5 blocks')
  assert np.allclose([low,high],entry['bounds_units'])
  report.append({'model':entry['id'],'checks':'PASS','cubes':len(els),'groups':len(groups),'min_y':round(float(low[1]),5),'total_height_blocks':round(float(high[1]/16),4)})
 result={'status':'PASS','models':len(report),'checks':['JSON parse','unique cube and group IDs','complete single-parent hierarchy','positive cube extents','finite coordinates','6 textured faces per cube','UV bounds 0..256','256x256 RGBA texture','external PNG equals embedded texture','rotated geometry bounds','feet y=0','total height exactly 40 units / 2.5 blocks'],'limitations':['Not opened in the Blockbench application in this environment.','Not exported to the target repository custom monster schema.','No Minecraft render, animation, material or collision integration test.'],'results':report}
 (ROOT/'VALIDATION.json').write_text(json.dumps(result,indent=2));print(json.dumps({'status':'PASS','models':len(report),'cubes_total':sum(x['cubes'] for x in report)},indent=2))
if __name__=='__main__':main()
