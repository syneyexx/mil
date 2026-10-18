"""Needleguy: cube-based Pinhead character asset. Python 3 + Pillow + NumPy."""
from asset_core import *

def recolor(m,mat,col):
 i=MATS.index(mat);x,y=i%4*64,i//4*64;a=np.array(m.tex);p=a[y:y+64,x:x+64,:3].astype(float);v=p.mean(2)-np.median(p.mean(2));a[y:y+64,x:x+64,:3]=np.clip(np.array(col)+v[:,:,None]*.65,0,255).astype('uint8');m.tex=Image.fromarray(a)

def head_atlas(m,head):
 # Dedicated bottom atlas strip: 4 pixels/unit for thin scars, no shared material UVs.
 regions={'north':(0,0,32,36),'south':(34,0,32,36),'east':(68,0,28,36),'west':(98,0,28,36),'up':(128,0,32,28),'down':(162,0,32,28)}
 d=ImageDraw.Draw(m.tex);rng=random.Random(44)
 for f,(u,v,w,h) in regions.items():
  x,y=u,192+v
  for yy in range(h):
   for xx in range(w):
    n=rng.choice([-3,-1,0,0,1,3]);m.tex.putpixel((x+xx,y+yy),(193+n,192+n,179+n,255))
  xs=range(8,w,8) if f=='west' else range(4,w,8)
  ys=range(8,h,8) if f!='down' else range(4,h,8)
  for xx in xs:d.line((x+xx,y,x+xx,y+h-1),fill=(132,107,101))
  for yy in ys:d.line((x,y+yy,x+w-1,y+yy),fill=(132,107,101))
  head['faces'][f]={'uv':[x,y,x+w,y+h],'texture':0}

def build():
 m=Model('needleguy','Needleguy',2.5);m.scale=1;c=m.cube
 for mat,col in [('skin',(191,190,177)),('bone',(201,201,189)),('cloth',(27,29,31)),('leather',(34,31,31)),('iron',(144,151,150)),('blood',(103,42,41)),('brass',(170,130,60))]:recolor(m,mat,col)
 m.group('body',(0,19,0));m.group('head',(0,30,0),'body');m.group('pins',(0,35,0),'head');m.group('collar',(0,29,0),'body')
 c('torso',(0,25,0),(10,12,5.8),'leather');c('pelvis',(0,18.5,0),(9,4,5.5),'leather')
 c('neck',(0,30,0),(3.6,3.2,3.6),'skin','head')
 head=c('scarred_head',(0,35.5,0),(8,9,7),'face','head');head_atlas(m,head)
 # Sculpted face; narrow eyes and a restrained neutral mouth.
 for s in [-1,1]:
  c('eye_socket',(s*1.8,36.1,-3.68),(1.65,.8,.35),'dark','head')
  c('eye_white',(s*1.8,36.05,-3.92),(1.1,.35,.2),'bone','head')
  c('iris',(s*1.8,36.05,-4.07),(.4,.4,.12),'dark','head')
  c('brow',(s*1.9,36.9,-3.66),(2,.4,.45),'skin','head',s*8)
  c('cheekbone',(s*2.4,34.3,-3.68),(1.8,.65,.4),'skin','head',s*-8)
 c('nose_bridge',(0,35.7,-3.84),(.7,1.6,.6),'skin','head');c('nose_tip',(0,34.9,-4.1),(1.1,.65,.8),'skin','head')
 c('mouth_line',(0,32.9,-3.66),(2.6,.3,.2),'blood','head');c('lower_lip',(0,32.55,-3.62),(2.3,.25,.2),'skin','head')
 # Nails arranged on the intersections of the front, side, rear and scalp grid.
 for side in [-1,1]:
  for x in [-3,-1,1,3]:
   for y in [32,34,36,38]:
    # Leave clearance around eyes and lips; the scar lattice continues underneath.
    if side==-1 and y==36 and abs(x)==1:continue
    c('front_pin' if side<0 else 'rear_pin',(x,y,side*4.3),(.16,.16,1.6),'iron','pins')
    c('pin_head',(x,y,side*5.22),(.46,.46,.24),'iron','pins')
  for z in [-2.5,-.5,1.5]:
   for y in [32,34,36,38]:
    c('side_pin',(side*4.8,y,z),(1.6,.16,.16),'iron','pins');c('pin_head',(side*5.72,y,z),(.24,.46,.46),'iron','pins')
 for x in [-3,-1,1,3]:
  for z in [-2.5,-.5,1.5]:
   c('scalp_pin',(x,40.8,z),(.16,1.6,.16),'iron','pins');c('pin_head',(x,41.72,z),(.46,.24,.46),'iron','pins')
 # Structured black leather with burgundy exposed panels and inset bindings.
 for s in [-1,1]:
  c('chest_inset',(s*2.55,25.8,-3.02),(3.8,7.7,.35),'blood')
  m.bar('diagonal_chest_binding',(s*4.3,29.3,-3.38),(s*.8,21.7,-3.38),1,'leather','body',.55)
  c('chest_edge',(s*4.65,25,-3.25),(.55,10,.6),'leather')
  c('high_collar',(s*3.1,29.5,0),(1.7,4,6.2),'leather','collar',s*10)
  for y in [23.2,26,28.6]:c('chest_fastener',(s*2.7,y,-3.6),(.75,.25,.3),'iron')
 c('center_binding',(0,25,-3.3),(1.1,11,.8),'leather')
 c('belt',(0,19.5,0),(10.5,1.7,6.7),'leather');c('belt_buckle',(0,19.5,-3.6),(2.2,1.5,.5),'iron');c('buckle_void',(0,19.5,-3.9),(1.3,.8,.2),'dark')
 for s,side in [(-1,'right'),(1,'left')]:
  m.group(side+'_arm',(s*6.5,29,0),'body');m.group(side+'_forearm',(s*7,23,0),side+'_arm');m.group(side+'_hand',(s*7,17,0),side+'_forearm')
  c('upper_sleeve',(s*6.5,26,0),(3.6,7,4.8),'leather',side+'_arm');c('shoulder_panel',(s*6.6,28.5,-.1),(4.5,2.3,5.5),'cloth',side+'_arm',s*8)
  c('lower_sleeve',(s*7,20.5,0),(3.3,6,4.3),'leather',side+'_forearm')
  for yy in [18.2,21.5]:c('sleeve_strap',(s*7,yy,0),(3.6,.65,4.6),'cloth',side+'_forearm')
  c('hand',(s*7,16,-.2),(2.6,2.6,2.8),'skin',side+'_hand')
  for j in range(3):c('finger',(s*(6.1+j*.85),14.1,-.45),(.6,1.4,1),'skin',side+'_hand')
  c('thumb',(s*5.45,16,-.5),(.7,1.5,1.4),'skin',side+'_hand',s*15)
  m.group(side+'_leg',(s*2.6,18,0));m.group(side+'_shin',(s*2.6,9,0),side+'_leg');m.group(side+'_robe',(s*2.6,18,0),side+'_leg')
  c('leg',(s*2.6,13,0),(3.9,10,4.6),'leather',side+'_leg');c('boot_shaft',(s*2.6,5.5,0),(4.1,7,4.8),'leather',side+'_shin');c('boot',(s*2.6,1.2,-1),(4.3,2.4,6.3),'leather',side+'_shin');c('sole',(s*2.6,.3,-1),(4.5,.6,6.5),'dark',side+'_shin')
  for j in range(3):
   h=14.8-j*.8;x=s*(1.3+j*1.7)
   for z in [-3.5,3.5]:c('split_leather_robe',(x,18-h/2,z),(1.65,h,.9),'leather' if j!=1 else 'cloth',side+'_robe',s*j*2)
  c('robe_outer',(s*5.8,10.8,0),(.8,14.4,7.7),'leather',side+'_robe',s*4)
  # Belt-hung surgical implements as readable blocky accessories.
  m.group(side+'_tools',(s*4.3,19,-3.8),'body')
  c('tool_loop',(s*4.3,18,-4.1),(1.2,1.4,.7),'cloth',side+'_tools');c('tool_handle',(s*4.3,15.4,-4.2),(.5,4,.5),'iron',side+'_tools')
  c('hanging_tool_blade',(s*4.3,12.9,-4.2),(1.1,2,.35),'iron',side+'_tools')
 # Compact gold puzzle cube held by the right hand.
 m.group('puzzle_cube',(-7,14,-.5),'right_hand');c('puzzle_core',(-7,11.8,-.5),(4.5,4.5,4.5),'brass','puzzle_cube')
 c('puzzle_front',(-7,11.8,-2.82),(3.9,3.9,.18),'dark','puzzle_cube')
 for x,y,w,h in [(-8.4,11.8,.25,3),(-7,13.2,3,.25),(-5.6,11.8,.25,3),(-6.3,10.4,1.5,.25),(-7,11.1,.25,1.5),(-7.7,11.8,1.5,.25)]:c('puzzle_inlay',(x,y,-2.98),(w,h,.15),'brass','puzzle_cube')
 # Normalize ALL geometry and pivots to exactly 40 units including nail heads.
 vv=np.concatenate([vertices(e) for e in m.elements]);factor=40/float(vv[:,1].max())
 for e in m.elements:
  for key in ['from','to','origin']:e[key]=[round(v*factor,7) for v in e[key]]
 for g in m.groups:g['origin']=[round(v*factor,7) for v in g['origin']]
 return m

FONT='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
def main():
 m=build();m.save();(ROOT/'previews').mkdir(exist_ok=True)
 for a,n in [(23,'3q'),(0,'front'),(155,'back')]:render(m,a,W=950,H=1250).save(ROOT/'previews'/f'needleguy_{n}.png')
 v=np.concatenate([vertices(e) for e in m.elements]);lo,hi=v.min(0),v.max(0)
 (ROOT/'manifest.json').write_text(json.dumps([{'id':m.slug,'name':m.name,'height_blocks':2.5,'total_height_blocks':float(hi[1]/16),'bounds_units':[lo.tolist(),hi.tolist()],'cube_count':len(m.elements),'group_count':len(m.groups),'model':f'mobs/{m.slug}/{m.slug}.bbmodel','texture':f'mobs/{m.slug}/{m.slug}.png','texture_size':[256,256],'animations_included':False}],indent=2))
 sheet=Image.new('RGB',(1350,1430),(12,17,19));d=ImageDraw.Draw(sheet);d.text((42,25),'NEEDLEGUY',font=ImageFont.truetype(FONT,48),fill=(226,213,183));d.text((44,88),'Pinhead-model • 2,5 blocks inclusief pinnen',font=ImageFont.truetype(FONT,23),fill=(154,168,166))
 sheet.paste(Image.open(ROOT/'previews/needleguy_3q.png'),(15,140))
 # Actual geometry close-up, cropped from a higher resolution front render.
 front=render(m,0,W=1200,H=1700);front.save(ROOT/'previews/needleguy_front_detail_source.png')
 headpic=front.crop((360,15,820,470)).resize((360,356),Image.Resampling.LANCZOS);sheet.paste(headpic,(970,170))
 d.text((985,550),'Raster + metalen pinnen',font=ImageFont.truetype(FONT,17),fill=(154,168,166));d.text((985,586),'256 x 256 texture',font=ImageFont.truetype(FONT,17),fill=(154,168,166));d.text((985,620),f'{len(m.elements)} cubes',font=ImageFont.truetype(FONT,17),fill=(154,168,166))
 d.text((43,1395),'Werkelijke modelrender • bewerkbaar .bbmodel',font=ImageFont.truetype(FONT,20),fill=(154,168,166));sheet.save(ROOT/'PREVIEW_NEEDLEGUY.png')
 print(json.dumps({'cubes':len(m.elements),'min_y':float(lo[1]),'max_y':float(hi[1]),'height_blocks':float(hi[1]/16)}))
if __name__=='__main__':main()
