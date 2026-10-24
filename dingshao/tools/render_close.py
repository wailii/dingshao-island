import sys, numpy as np
from PIL import Image
N, SS = 96, 4
ys, xs = np.mgrid[0:N*SS, 0:N*SS].astype(np.float32); xs=(xs+.5)/SS; ys=(ys+.5)/SS
# 红色圆底 + 白色 ✕
disc = (xs-48)**2 + (ys-48)**2 <= 44**2
def seg(x0,y0,x1,y1,w):
    dx,dy=x1-x0,y1-y0; L=dx*dx+dy*dy
    t=np.clip(((xs-x0)*dx+(ys-y0)*dy)/L,0,1)
    return (xs-(x0+t*dx))**2+(ys-(y0+t*dy))**2 <= (w/2)**2
cross = seg(32,32,64,64,11) | seg(64,32,32,64,11)
a_red = disc.astype(np.float32).reshape(N,SS,N,SS).mean(axis=(1,3))
a_x = (disc & cross).astype(np.float32).reshape(N,SS,N,SS).mean(axis=(1,3))
img = np.zeros((N,N,4),np.uint8)
r = 255*(1-a_x) + 255*a_x; g = 59*(1-a_x) + 255*a_x; b = 43*(1-a_x) + 255*a_x
img[...,0]=r.round(); img[...,1]=g.round(); img[...,2]=b.round(); img[...,3]=(a_red*255).round()
Image.fromarray(img.astype(np.uint8),"RGBA").save(sys.argv[1])
