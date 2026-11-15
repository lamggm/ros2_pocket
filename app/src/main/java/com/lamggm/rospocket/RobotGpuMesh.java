package com.lamggm.rospocket;

import android.graphics.Color;
import java.nio.*;
import java.util.Arrays;

/** Immutable direct vertex payload: xyz, normal, rgba. No Android GL context needed to prepare. */
final class RobotGpuMesh {
 static final int MAX_TRIANGLES=120000,STRIDE=10;
 static final class Data {
  final RobotCad.Scene cad;final FloatBuffer vertices;final int triangleCount;final double cx,cy,cz,radius;
  Data(RobotCad.Scene s){cad=s;vertices=ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder()).asFloatBuffer();triangleCount=s.triangles;cx=s.cx;cy=s.cy;cz=s.cz;radius=s.radius;}
  Data(FloatBuffer v,int count,double x,double y,double z,double r){cad=null;vertices=v.asReadOnlyBuffer();triangleCount=count;cx=x;cy=y;cz=z;radius=r;}
 }
  // Full CAD keeps unique indexed buffers and instances; no per-face Triangle objects.
 static Data full(RobotCad.Scene scene){return new Data(scene);}
 static final class Builder {
  private final int limit;private float[] values;private int used,count;private boolean invalid,finished;
  Builder(int n){if(n<1||n>MAX_TRIANGLES)throw new IllegalArgumentException("Limite GPU inválido");limit=n;values=new float[Math.min(4096,n*3*STRIDE)];}
  private IllegalArgumentException fail(){invalid=true;values=null;return new IllegalArgumentException("Geometria GPU inválida ou excede "+limit+" faces visuais");}
  void add(RobotDescription.Visual v,double[] a,double[] b,double[] c,int color){add(v,a,b,c,color,null);}
  void add(RobotDescription.Visual v,double[] a,double[] b,double[] c,int color,double[] normals){
   if(invalid||finished||count>=limit)throw fail();double[][] points={RobotDescription.point(v.transform,a[0],a[1],a[2]),RobotDescription.point(v.transform,b[0],b[1],b[2]),RobotDescription.point(v.transform,c[0],c[1],c[2])};
   for(double[] p:points)for(double d:p)if(!Double.isFinite(d)||Math.abs(d)>1e8)throw fail();
   double ax=points[1][0]-points[0][0],ay=points[1][1]-points[0][1],az=points[1][2]-points[0][2],bx=points[2][0]-points[0][0],by=points[2][1]-points[0][1],bz=points[2][2]-points[0][2];double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx,n=Math.sqrt(nx*nx+ny*ny+nz*nz);if(n<1e-20){nx=0;ny=0;nz=1;n=1;}
   double[][] smooth=null;if(normals!=null){if(normals.length!=9)throw fail();smooth=new double[3][];try{for(int k=0;k<3;k++)smooth[k]=RobotNormals.transform(v.transform,normals[k*3]/v.size[0],normals[k*3+1]/v.size[1],normals[k*3+2]/v.size[2]);}catch(IllegalArgumentException e){throw fail();}}
   int needed=used+3*STRIDE;if(needed>values.length)values=Arrays.copyOf(values,Math.min(limit*3*STRIDE,Math.max(needed,values.length*2)));
   for(int k=0;k<3;k++){double[] p=points[k];values[used++]=(float)p[0];values[used++]=(float)p[1];values[used++]=(float)p[2];values[used++]=(float)(smooth==null?nx/n:smooth[k][0]);values[used++]=(float)(smooth==null?ny/n:smooth[k][1]);values[used++]=(float)(smooth==null?nz/n:smooth[k][2]);values[used++]=Color.red(color)/255f;values[used++]=Color.green(color)/255f;values[used++]=Color.blue(color)/255f;values[used++]=Color.alpha(color)/255f;}count++;
  }
  Data finish(){if(invalid||finished)throw fail();finished=true;double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};for(int i=0;i<used;i+=STRIDE)for(int k=0;k<3;k++){min[k]=Math.min(min[k],values[i+k]);max[k]=Math.max(max[k],values[i+k]);}double x=count==0?0:(min[0]+max[0])/2,y=count==0?0:(min[1]+max[1])/2,z=count==0?0:(min[2]+max[2])/2,r=0;for(int i=0;i<used;i+=STRIDE){double a=values[i]-x,b=values[i+1]-y,c=values[i+2]-z;r=Math.max(r,Math.sqrt(a*a+b*b+c*c));}FloatBuffer buffer=ByteBuffer.allocateDirect(used*4).order(ByteOrder.nativeOrder()).asFloatBuffer();buffer.put(values,0,used).position(0);values=null;return new Data(buffer,count,x,y,z,Math.max(r,.001));}
 }
}
