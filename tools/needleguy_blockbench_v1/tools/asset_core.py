"""Deterministic cube-based Blockbench asset generator. Python 3 + Pillow + numpy."""
from pathlib import Path
import json, math, uuid, base64, random, io
import numpy as np
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]
MATS=['leather','skin','bone','iron','brass','cloth','paper','wood','mirror','dark','blood','amber','face','stitch','purple','ghost']
COLORS=[(57,40,31),(155,126,103),(195,183,149),(77,80,77),(150,113,52),(39,37,34),(184,164,119),(94,64,38),(156,172,170),(23,24,23),(84,35,32),(238,158,45),(191,176,142),(106,79,63),(68,39,75),(185,203,201)]
def uid(s): return str(uuid.uuid5(uuid.NAMESPACE_URL,'needleguy-v1/'+s))
def atlas(seed):
 r=random.Random(seed); im=Image.new('RGBA',(256,256)); d=ImageDraw.Draw(im)
 for i,(mat,col) in enumerate(zip(MATS,COLORS)):
  x,y=i%4*64,i//4*64
  for v in range(64):
   for u in range(64):
    n=r.choice([-10,-5,0,0,0,5,9]); stripe=3 if mat in ['cloth','purple','wood'] and u%7<2 else 0
    im.putpixel((x+u,y+v),tuple(max(0,min(255,c+n+stripe)) for c in col)+(255,))
  if mat in ['leather','cloth','purple','stitch']:
   for k in range(4,64,16):
    d.line((x+k,y,x+k,y+63),fill=tuple(int(c*.68) for c in col),width=1)
    for z in range(4,64,6):d.line((x+k-1,y+z,x+k+1,y+z),fill=(133,106,73))
  if mat in ['skin','bone','face','ghost']:
   for k in range(7):
    a,b=r.randrange(6,58),r.randrange(4,52)
    d.line([(x+a,y+b),(x+a-2,y+b+4),(x+a+1,y+b+7),(x+a-1,y+b+12)], fill=(88,70,58) if mat!='ghost' else (128,151,150))
  if mat in ['iron','brass','mirror']:
   for k in range(3):
    a,b=5+k*19,5+k*17;d.line((x+a,y+b,x+a+14,y+b),fill=tuple(min(255,c+42) for c in col));d.line((x+a,y+b,x+a,y+b+12),fill=tuple(int(c*.6) for c in col))
   if mat=='mirror':
    for k in range(0,64,14): d.line((x+k,y,x+min(63,k+21),y+63),fill=(63,69,65),width=2)
  if mat=='paper':
   for yy in range(6,60,5):
    for xx in range(5,56,9):d.line((x+xx,y+yy,x+xx+r.randint(3,7),y+yy),fill=(97,77,50))
  if mat=='wood':
   for xx in range(5,62,9): d.line((x+xx,y,x+xx+1,y+63),fill=(48,33,24))
  if mat=='amber':
   d.rectangle((x+8,y+8,x+55,y+55),fill=(255,191,68));d.rectangle((x+20,y+16,x+43,y+48),fill=(255,225,133))
 return im
class Model:
 def __init__(self,slug,name,height):
  self.slug,self.name,self.height=slug,name,height;self.serial=0;self.elements=[];self.groups=[];self.nodes={};self.scale=height*16/40;self.xoff=0;self.prefix=''; self.tex=atlas(slug)
  self.group('root',(0,0,0),None)
 def point(self,p):return [round((p[0]+self.xoff)*self.scale,5),round(p[1]*self.scale,5),round(p[2]*self.scale,5)]
 def group(self,name,origin,parent='root'):
  key=self.prefix+name if name!='root' else name
  parent=(self.prefix+parent if parent not in ['root',None] else parent)
  g={'name':key,'origin':self.point(origin),'rotation':[0,0,0],'uuid':uid(self.slug+'/g/'+key),'export':True,'isOpen':False,'visibility':True,'children':[]}
  self.groups.append(g);self.nodes[key]=g
  if parent:self.nodes[parent]['children'].append(g)
  return key
 def cube(self,name,c,size,mat='leather',group='body',rot=0):
  key=self.prefix+group if group!='root' else group
  if key not in self.nodes:self.group(group,c)
  c=self.point(c);s=[v*self.scale for v in size];n=self.serial;self.serial+=1;tile=MATS.index(mat); tx,ty=tile%4*64,tile//4*64
  faces={}
  for f,wh in [('north',(s[0],s[1])),('south',(s[0],s[1])),('east',(s[2],s[1])),('west',(s[2],s[1])),('up',(s[0],s[2])),('down',(s[0],s[2]))]:
   w,h=[max(1,min(56,round(v*2))) for v in wh];ox=(n*7)%max(1,61-w);oy=(n*11)%max(1,61-h)
   faces[f]={'uv':[tx+ox,ty+oy,tx+ox+w,ty+oy+h],'texture':0}
  e={'name':name,'type':'cube','uuid':uid(self.slug+'/e/'+str(n)),'from':[round(c[i]-s[i]/2,5) for i in range(3)],'to':[round(c[i]+s[i]/2,5) for i in range(3)],'origin':c,'rotation':[0,0,rot],'inflate':0,'box_uv':False,'uv_offset':[0,0],'faces':faces,'visibility':True,'export':True,'color':tile%8}
  self.elements.append(e);self.nodes[key]['children'].append(e['uuid']);return e
 def bar(self,name,a,b,width,mat='brass',group='body',depth=None):
  dx,dy=b[0]-a[0],b[1]-a[1];length=math.hypot(dx,dy)
  return self.cube(name,[(a[i]+b[i])/2 for i in range(3)],(width,length,depth or width),mat,group,-math.degrees(math.atan2(dx,dy)))
 def ring(self,name,x,y,z,r=1.5,mat='brass',group='body',thick=.4):
  for j in range(8):
   a=j*math.tau/8;b=(j+1)*math.tau/8
   self.bar(name,(x+r*math.cos(a),y+r*math.sin(a),z),(x+r*math.cos(b),y+r*math.sin(b),z),thick,mat,group)
 def chain(self,name,x,y,z,length,group='body',mat='brass'):
  for j in range(max(1,int(length/1.8))):
   self.ring(name,x+.3*math.sin(j),y-j*1.8,z,.85,mat,group,.22)
 def belt(self,y=20,width=11):
  self.cube('waist_belt',(0,y,0),(width,1.7,6.8),'leather'); self.ring('belt_buckle',0,y,-3.65,1,'brass','body',.4)
 def coat(self,mat='cloth',long=True):
  for side in [-1,1]:
   self.cube('coat_lapel',(side*3.5,27,-3.5),(2.2,11,1),mat,rot=side*8)
   for j in range(3):self.cube('torn_coat_panel',(side*(2+j*1.8),10+(j%2),.4),(1.8,16-2*(j%2),7.2),mat,'coat',side*(j+1)*3)
  self.belt()
 def face(self,style='mask'):
  skin='bone' if style=='mask' else 'skin'
  if style=='ghost':skin='ghost'
  self.cube('face_plate',(0,35.4,-3.15),(6.6,7.6,.75),skin,'head')
  for s in [-1,1]:
   self.cube('eye_socket',(s*1.75,36.5,-3.62),(1.6,1.1,.3),'dark','head')
   if style in ['doll','ghost']:self.cube('eye_glint',(s*1.75,36.5,-3.82),(.45,.45,.25),'blood','head')
  self.cube('nose',(0,35.1,-3.8),(.9,1.3,.7),skin,'head')
  self.cube('mouth',(0,33.4,-3.65),(3.6,.5,.3),'dark','head')
  for x in [-1.4,-.7,0,.7,1.4]:self.cube('mouth_stitch',(x,33.4,-3.88),(.18,.95,.2),'iron','head')
 def hood(self):
  for s in [-1,1]:self.cube('hood_side',(s*4.1,35,.2),(1.5,9.5,7.8),'cloth','head')
  self.cube('hood_top',(0,39.4,.2),(9.7,1.2,7.8),'cloth','head');self.cube('hood_back',(0,35,3.6),(9,8,1),'cloth','head')
 def humanoid(self,skin='skin',clothes='leather',slim=False,head=True):
  self.group('body',(0,20,0));self.group('head',(0,31,0),'body');self.group('coat',(0,20,0),'body')
  self.cube('torso',(0,25,0),(8 if slim else 10,12,5.5),clothes)
  self.cube('neck',(0,31,0),(3,2,3),skin,'head')
  if head:self.cube('skull',(0,35.5,0),(7.6,9,6),skin,'head')
  for s,side in [(-1,'right'),(1,'left')]:
   self.group(side+'_arm',(s*6.5,30,0),'body'); self.group(side+'_forearm',(s*7,24,0),side+'_arm')
   self.group(side+'_hand',(s*7,18,0),side+'_forearm')
   self.cube('upper_arm',(s*6.5,27,0),(2.8 if slim else 3.5,7,4),clothes,side+'_arm')
   self.cube('forearm',(s*7,21,0),(2.3 if slim else 3.1,6,3.5),skin,side+'_forearm')
   self.cube('hand',(s*7,17,0),(2.5,3,2.6),skin,side+'_hand')
   for j in range(3):self.cube('finger',(s*(6.2+j*.75),14.9,-.2),(.5,1.6,.65),skin,side+'_hand')
   self.group(side+'_leg',(s*2.7,19,0));self.group(side+'_shin',(s*2.7,10,0),side+'_leg')
   self.cube('thigh',(s*2.7,14.5,0),(3.8 if not slim else 2.6,9,4),clothes,side+'_leg')
   self.cube('shin',(s*2.7,6,0),(3.5 if not slim else 2.4,8,3.5),skin if slim else clothes,side+'_shin')
   self.cube('foot',(s*2.7,1,-1),(4 if not slim else 2.8,2,6),skin if slim else 'leather',side+'_shin')
 def straps(self):
  for y in [23,27]:self.cube('chest_strap',(0,y,-3),(10.3,1,1),'leather')
  self.bar('diagonal_harness',(-4,30,-3.5),(4,20,-3.5),1.2,'leather');self.ring('harness_ring',0,25,-4,1)
 def armor(self):
  for s,side in [(-1,'right'),(1,'left')]:
   self.cube('pauldron',(s*7,29,0),(6,5,7),'iron',side+'_arm')
   self.cube('bracer',(s*7,21,0),(4.5,5,4.8),'iron',side+'_forearm')
   for y in [27.6,30.5]:
    for x in [s*7-2,s*7+2]:self.cube('rivet',(x,y,-3.6),(.55,.55,.5),'brass',side+'_arm')
  self.cube('breastplate',(0,26,-3),(10.8,8,1.6),'iron');self.belt()
 def key(self,x,y,z,size=1,group='body'):
  self.ring('key_bow',x,y,z,1.6*size,'brass',group,.5*size);self.cube('key_shaft',(x,y-4*size,z),(.6*size,5*size,.6*size),'brass',group)
  for yy in [y-5*size,y-6*size]:self.cube('key_tooth',(x+.7*size,yy,z),(1.5*size,.5*size,.7*size),'brass',group)
 def bell(self,x,y,z,r=2,group='body'):
  self.ring('bell_loop',x,y+r+1,z,.6,'brass',group,.3)
  self.cube('bell_crown',(x,y+r*.6,z),(r,r,1.3*r),'brass',group)
  self.cube('bell_body',(x,y,z),(r*1.6,r,1.6*r),'brass',group)
  self.cube('bell_lip',(x,y-r*.6,z),(r*2,.6,2*r),'brass',group)
  self.cube('bell_dark_opening',(x,y-r*.8,z),(r*1.5,.25,r*1.5),'dark',group)
  self.cube('clapper',(x,y-r,z),(.55,1,.55),'iron',group)
 def save(self):
  folder=ROOT/'mobs'/self.slug;folder.mkdir(parents=True,exist_ok=True);self.tex.save(folder/(self.slug+'.png'))
  out=io.BytesIO();self.tex.save(out,format='PNG')
  data={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':self.name,'model_identifier':self.slug,'visible_box':[6,6,2],'variable_placeholders':'','resolution':{'width':256,'height':256},'elements':self.elements,'outliner':[self.nodes['root']],'textures':[{'path':self.slug+'.png','relative_path':self.slug+'.png','name':self.slug+'.png','folder':'','namespace':'','id':'0','uuid':uid(self.slug+'/texture'),'width':256,'height':256,'uv_width':256,'uv_height':256,'render_mode':'default','render_sides':'auto','mode':'bitmap','saved':True,'source':'data:image/png;base64,'+base64.b64encode(out.getvalue()).decode()}],'animations':[]}
  (folder/(self.slug+'.bbmodel')).write_text(json.dumps(data,ensure_ascii=False,indent=2))
  return data
def vertices(e):
 a,b=np.array(e['from']),np.array(e['to']);p=np.array([[a[0],a[1],a[2]],[b[0],a[1],a[2]],[b[0],b[1],a[2]],[a[0],b[1],a[2]],[a[0],a[1],b[2]],[b[0],a[1],b[2]],[b[0],b[1],b[2]],[a[0],b[1],b[2]]])
 angle=math.radians(e['rotation'][2]);R=np.array([[math.cos(angle),-math.sin(angle),0],[math.sin(angle),math.cos(angle),0],[0,0,1]])
 o=np.array(e['origin']);return (p-o)@R.T+o
FACES={'north':[3,2,1,0],'south':[6,7,4,5],'east':[2,6,5,1],'west':[7,3,0,4],'up':[7,6,2,3],'down':[0,1,5,4]}
def render(m,angle=24,W=500,H=620):
 yaw=math.radians(angle);pitch=math.radians(9);right=np.array([math.cos(yaw),0,math.sin(yaw)]);up=np.array([-math.sin(yaw)*math.sin(pitch),math.cos(pitch),math.cos(yaw)*math.sin(pitch)]);toward=np.cross(right,up)
 P=np.stack([right,-up,toward],axis=1);allv=np.concatenate([vertices(e) for e in m.elements]);proj=allv@P
 extent=proj.max(0)-proj.min(0);sc=min((W-55)/extent[0],(H-85)/extent[1]);mid=(proj.max(0)+proj.min(0))/2
 pixels=np.zeros((H,W,3),dtype=np.uint8);pixels[:]=[22,26,28];depth=np.full((H,W),np.inf);tex=np.array(m.tex)[:,:,:3]
 for e in m.elements:
  v=vertices(e);q=v@P;q[:,:2]=(q[:,:2]-mid[:2])*sc+[W/2,H/2-5]
  for f,ids in FACES.items():
   vv=v[ids];normal=np.cross(vv[1]-vv[0],vv[2]-vv[0]);normal=normal/(np.linalg.norm(normal)+1e-9)
   # Both sides rasterized, z buffer selects nearest face.
   light=.64+.36*abs(np.dot(normal,np.array([-.4,.7,-.59])))
   u0,v0,u1,v1=e['faces'][f]['uv'];uv=np.array([[u0,v0],[u1,v0],[u1,v1],[u0,v1]])
   for inds in [[0,1,2],[0,2,3]]:
    t=q[np.array(ids)[inds]];ut=uv[inds];lo=np.maximum(0,np.floor(t[:,:2].min(0)).astype(int));hi=np.minimum([W-1,H-1],np.ceil(t[:,:2].max(0)).astype(int))
    if np.any(lo>hi):continue
    xx,yy=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
    den=(t[1,1]-t[2,1])*(t[0,0]-t[2,0])+(t[2,0]-t[1,0])*(t[0,1]-t[2,1])
    if abs(den)<1e-8:continue
    a=((t[1,1]-t[2,1])*(xx-t[2,0])+(t[2,0]-t[1,0])*(yy-t[2,1]))/den;b=((t[2,1]-t[0,1])*(xx-t[2,0])+(t[0,0]-t[2,0])*(yy-t[2,1]))/den;cc=1-a-b
    z=a*t[0,2]+b*t[1,2]+cc*t[2,2];sub=depth[lo[1]:hi[1]+1,lo[0]:hi[0]+1];mask=(a>=0)&(b>=0)&(cc>=0)&(z<sub)
    u=np.clip((a*ut[0,0]+b*ut[1,0]+cc*ut[2,0]).astype(int),0,255);tv=np.clip((a*ut[0,1]+b*ut[1,1]+cc*ut[2,1]).astype(int),0,255)
    rgb=np.clip(tex[tv,u]*light,0,255).astype(np.uint8);pixels[lo[1]:hi[1]+1,lo[0]:hi[0]+1][mask]=rgb[mask];sub[mask]=z[mask]
 return Image.fromarray(pixels)
