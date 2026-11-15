package com.lamggm.rospocket;

/** Normal directions require inverse-transpose, never position translation. */
final class RobotNormals {
 static double[] transform(double[] m,double x,double y,double z){
  double s=0;for(int r=0;r<3;r++)for(int c=0;c<3;c++){double v=m[r*4+c];if(!Double.isFinite(v))throw new IllegalArgumentException("Matriz normal inválida");s=Math.max(s,Math.abs(v));}if(s==0||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Normal inválida");
  double a=m[0]/s,b=m[1]/s,c=m[2]/s,d=m[4]/s,e=m[5]/s,f=m[6]/s,g=m[8]/s,h=m[9]/s,i=m[10]/s;
  double A=e*i-f*h,B=f*g-d*i,C=d*h-e*g,D=c*h-b*i,E=a*i-c*g,F=b*g-a*h,G=b*f-c*e,H=c*d-a*f,I=a*e-b*d,det=a*A+b*B+c*C;
  double nx=A*x+B*y+C*z,ny=D*x+E*y+F*z,nz=G*x+H*y+I*z,n=Math.hypot(Math.hypot(nx,ny),nz);if(det==0||!Double.isFinite(det)||n==0||!Double.isFinite(n))throw new IllegalArgumentException("Normal singular ou nula");double sign=det<0?-1:1;return new double[]{sign*nx/n,sign*ny/n,sign*nz/n};
 }
}
