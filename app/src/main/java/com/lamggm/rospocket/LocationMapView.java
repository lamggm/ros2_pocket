package com.lamggm.rospocket;

import android.content.Context;import android.graphics.*;import android.view.*;
/** Local metric coordinates only: no internet tiles, goals, GPS or command gestures. */
final class LocationMapView extends View {
 private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);private MapGrid map;
 private double centerX,centerY,zoom=1,x,y,yaw;private long poseDeadline;private boolean hasPose,follow;
 private float lastX,lastY;private final ScaleGestureDetector pinch;private Runnable expiry;private Runnable expired=()->{},followChanged=()->{};
 LocationMapView(Context c){super(c);setTag("location-surface");setContentDescription("Mapa ROS 2 somente leitura; arraste e faça pinça para navegar");pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){follow=false;followChanged.run();zoom=Math.max(.1,Math.min(64,zoom*d.getScaleFactor()));invalidate();return true;}});}
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 void onPoseExpired(Runnable r){expired=r;}
 void onFollowChanged(Runnable r){followChanged=r;}
 void map(MapGrid next){boolean first=map==null;boolean same=map!=null&&map.frame.equals(next.frame);map=next;if(!same)clearPose();if(first||!same)fit();invalidate();}
 MapGrid map(){return map;}
 void clear(){map=null;clearPose();follow=false;followChanged.run();zoom=1;invalidate();}
 void clearPose(){hasPose=false;if(expiry!=null)removeCallbacks(expiry);expiry=null;invalidate();}
 void pose(double px,double py,double heading,long deadline){if(!Double.isFinite(px)||!Double.isFinite(py)||!Double.isFinite(heading)||Math.abs(px)>1e8||Math.abs(py)>1e8||System.nanoTime()>deadline){clearPose();return;}x=px;y=py;yaw=heading;poseDeadline=deadline;hasPose=true;if(follow){centerX=x;centerY=y;}if(expiry!=null)removeCallbacks(expiry);if(deadline!=Long.MAX_VALUE){final long own=deadline;expiry=()->{if(hasPose&&poseDeadline==own&&System.nanoTime()>own){clearPose();expired.run();}};postDelayed(expiry,Math.max(1,(deadline-System.nanoTime())/1000000+1));}invalidate();}
 boolean poseVisible(){return hasPose&&map!=null&&System.nanoTime()<=poseDeadline;}
 void fit(){if(map!=null){double[] c=map.world(map.width*.5,map.height*.5);centerX=c[0];centerY=c[1];}zoom=1;follow=false;followChanged.run();invalidate();}
 void zoom(double factor){follow=false;followChanged.run();zoom=Math.max(.1,Math.min(64,zoom*factor));invalidate();}
 boolean follow(){return follow;}
 void follow(boolean value){follow=value;followChanged.run();if(value&&poseVisible()){centerX=x;centerY=y;}invalidate();}
 private double scale(){if(map==null)return 1;double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;for(int u:new int[]{0,map.width})for(int v:new int[]{0,map.height}){double[] p=map.world(u,v);minX=Math.min(minX,p[0]);minY=Math.min(minY,p[1]);maxX=Math.max(maxX,p[0]);maxY=Math.max(maxY,p[1]);}return Math.min(Math.max(1,getWidth())/Math.max(1e-9,maxX-minX),Math.max(1,getHeight())/Math.max(1e-9,maxY-minY))*.88*zoom;}
 float[] screen(double wx,double wy){double s=scale();return new float[]{(float)(getWidth()*.5+(wx-centerX)*s),(float)(getHeight()*.5-(wy-centerY)*s)};}
 @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(VisualButton.bg(getContext()));if(map==null){pen.setColor(VisualButton.muted(getContext()));pen.setTextSize(dp(14));pen.setTextAlign(Paint.Align.CENTER);c.drawText("Aguardando mapa DDS",getWidth()*.5f,getHeight()*.5f,pen);return;}
  float[] src={0,0,map.width,0,0,map.height};double[] a=map.world(0,0),b=map.world(map.width,0),d=map.world(0,map.height);float[] sa=screen(a[0],a[1]),sb=screen(b[0],b[1]),sd=screen(d[0],d[1]);float[] dst={sa[0],sa[1],sb[0],sb[1],sd[0],sd[1]};Matrix m=new Matrix();m.setPolyToPoly(src,0,dst,0,3);pen.setAlpha(255);pen.setFilterBitmap(false);c.drawBitmap(map.bitmap,m,pen);
  if(poseVisible()){float[] p=screen(x,y);if(Float.isFinite(p[0])&&Float.isFinite(p[1])&&p[0]>=-dp(20)&&p[0]<=getWidth()+dp(20)&&p[1]>=-dp(20)&&p[1]<=getHeight()+dp(20)){c.save();c.translate(p[0],p[1]);c.rotate((float)-Math.toDegrees(yaw));Path triangle=new Path();triangle.moveTo(dp(13),0);triangle.lineTo(-dp(9),-dp(8));triangle.lineTo(-dp(5),0);triangle.lineTo(-dp(9),dp(8));triangle.close();pen.setColor(0xff087f67);pen.setStyle(Paint.Style.FILL);c.drawPath(triangle,pen);pen.setStrokeWidth(dp(2));pen.setStyle(Paint.Style.STROKE);pen.setColor(0xffffffff);c.drawPath(triangle,pen);pen.setStyle(Paint.Style.FILL);c.restore();}}
 }
 @Override public boolean onTouchEvent(MotionEvent e){pinch.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();getParent().requestDisallowInterceptTouchEvent(true);return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){if(!pinch.isInProgress()&&e.getPointerCount()==1){double s=scale();follow=false;followChanged.run();centerX-=(e.getX()-lastX)/s;centerY+=(e.getY()-lastY)/s;invalidate();}lastX=e.getX();lastY=e.getY();return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}return true;}
 @Override public boolean performClick(){super.performClick();return true;}
 @Override protected void onDetachedFromWindow(){clearPose();super.onDetachedFromWindow();}
}
