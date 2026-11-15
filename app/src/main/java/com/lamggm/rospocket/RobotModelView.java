package com.lamggm.rospocket;

import android.content.Context;
import android.graphics.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.FrameLayout;
import android.opengl.GLSurfaceView;
import android.app.ActivityManager;
import java.util.function.Consumer;
import java.util.*;

/** Local 3D projection of received URDF visuals, never a robot pose/command. */
final class RobotModelView extends FrameLayout {
 static final int MAX_FACES=8192;
 private static final class Face {final double[][] vertices;final int color;final float[] xy=new float[6];final double[] z=new double[3];double depth,light;Face(double[][] v,int c){vertices=v;color=c;}}
 private final List<Face> faces=new ArrayList<>();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private final ScaleGestureDetector pinch;
 private Bitmap raster;private int[] rasterColors;private float[] rasterDepth;private boolean translucent;
 private GLSurfaceView gpu;private RobotGpuRenderer driver;private RobotGpuMesh.Builder gpuBuilder;private RobotGpuMesh.Data gpuData;private String gpuFailure="";private Consumer<String> failureListener=s->{};
 private Map<String,double[]> transforms;
 void setTransforms(Map<String,double[]> value){if(transforms==value)return;transforms=value;redraw();}
 int geometryFaces(){return gpuData==null?faces.size():gpuData.triangleCount;}
 private RobotDescription model;private double cx,cy,cz,radius=1;private float previousX,previousY;private boolean touching;private float zoom=1;
 double yaw=.65,pitch=.4;int drawnFaces;boolean autoRotate=true;boolean frameScheduled;
 private boolean screenActive,attached;private long lastFrame;private final Rect visibleBounds=new Rect();
 private final ViewTreeObserver.OnScrollChangedListener scrolling=this::updateAnimation;
 private final ViewTreeObserver.OnGlobalLayoutListener layout=this::updateAnimation;
 final Choreographer.FrameCallback animation=new Choreographer.FrameCallback(){public void doFrame(long t){frameScheduled=false;if(!canOrbit()){lastFrame=0;return;}if(lastFrame>0)yaw=(yaw+Math.max(0,Math.min(.1,(t-lastFrame)/1e9))*.12)%(Math.PI*2);lastFrame=t;redraw();scheduleFrame();}};
 void setScreenActive(boolean value){screenActive=value;updateAnimation();if(value)redraw();}
 void setAutoRotate(boolean value){autoRotate=value;updateAnimation();}
 private boolean canOrbit(){return attached&&screenActive&&autoRotate&&!touching&&gpuFailure.isEmpty()&&(gpu!=null?gpuData!=null&&gpuData.triangleCount>0:!faces.isEmpty())&&getWindowVisibility()==VISIBLE&&isShown()&&getGlobalVisibleRect(visibleBounds)&&!visibleBounds.isEmpty();}
 private void scheduleFrame(){if(!frameScheduled&&canOrbit()){frameScheduled=true;Choreographer.getInstance().postFrameCallbackDelayed(animation,gpu!=null?33:faces.size()>2000?66:33);}}
 private void updateAnimation(){if(animation==null)return;if(canOrbit())scheduleFrame();else{Choreographer.getInstance().removeFrameCallback(animation);frameScheduled=false;lastFrame=0;}}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;getViewTreeObserver().addOnScrollChangedListener(scrolling);getViewTreeObserver().addOnGlobalLayoutListener(layout);updateAnimation();redraw();}
 @Override protected void onDetachedFromWindow(){attached=false;updateAnimation();releaseRaster();if(getViewTreeObserver().isAlive()){getViewTreeObserver().removeOnScrollChangedListener(scrolling);getViewTreeObserver().removeOnGlobalLayoutListener(layout);}super.onDetachedFromWindow();}
 @Override protected void onVisibilityChanged(View v,int visibility){super.onVisibilityChanged(v,visibility);updateAnimation();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);updateAnimation();}
 RobotModelView(Context c){super(c);setWillNotDraw(false);setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setContentDescription("Modelo 3D · aguardando descrição");pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){zoom=Math.max(.35f,Math.min(4,zoom*d.getScaleFactor()));invalidate();return true;}});ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);if(manager!=null&&RobotGpuRenderer.supportsVersion(manager.getDeviceConfigurationInfo().reqGlEsVersion)){driver=new RobotGpuRenderer((failed,message)->post(()->{if(failed!=null&&failed==gpuData){gpuFailure=message;gpu.setVisibility(GONE);updateAnimation();failureListener.accept(message);super.invalidate();}}));gpu=new GLSurfaceView(c);gpu.setEGLContextClientVersion(2);gpu.setEGLConfigChooser(new RobotGpuRenderer.AntialiasConfig());gpu.setPreserveEGLContextOnPause(true);gpu.setRenderer(driver);gpu.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);gpu.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);gpu.setOnTouchListener((v,event)->onTouchEvent(event));gpu.setVisibility(GONE);addView(gpu,new FrameLayout.LayoutParams(-1,-1));}}
 void setFailureListener(Consumer<String> listener){failureListener=listener;}
 private void redraw(){if(gpu!=null){driver.scene(gpuData,yaw,pitch,zoom,VisualButton.bg(getContext()),transforms);if(screenActive&&isShown()&&getWindowVisibility()==VISIBLE&&getGlobalVisibleRect(visibleBounds))gpu.requestRender();}else invalidate();}
 @Override public void invalidate(){super.invalidate();if(gpu!=null)redraw();}
 void setModel(RobotDescription value){setModel(value,null);}
 void setModel(RobotDescription value,RobotAssetBundle assets){
  transforms=null;
  if(value!=null&&assets!=null&&assets.fullGeometry()&&assets.matches(value)){setFullModel(value,assets);return;}
  faces.clear();model=value;drawnFaces=0;zoom=1;translucent=false;gpuFailure="";gpuBuilder=gpu==null?null:new RobotGpuMesh.Builder(RobotGpuMesh.MAX_TRIANGLES);
  if(value!=null)for(RobotDescription.Visual visual:value.visuals){
   switch(visual.kind){case "box":box(visual);break;case "cylinder":cylinder(visual);break;case "sphere":sphere(visual);break;case "mesh":if(assets!=null&&assets.matches(value))mesh(visual,assets.triangles(visual.mesh));break;default:break;}
  }
  if(gpu!=null){gpuData=gpuBuilder.finish();gpuBuilder=null;driver.scene(gpuData,yaw,pitch,zoom,VisualButton.bg(getContext()));gpu.setVisibility(gpuData.triangleCount>0?VISIBLE:GONE);}
  if(!faces.isEmpty()){double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};for(Face f:faces)for(double[] p:f.vertices)for(int i=0;i<3;i++){min[i]=Math.min(min[i],p[i]);max[i]=Math.max(max[i],p[i]);}cx=(min[0]+max[0])/2;cy=(min[1]+max[1])/2;cz=(min[2]+max[2])/2;radius=0;for(Face f:faces)for(double[] p:f.vertices)radius=Math.max(radius,Math.sqrt((p[0]-cx)*(p[0]-cx)+(p[1]-cy)*(p[1]-cy)+(p[2]-cz)*(p[2]-cz)));radius=Math.max(radius,.001);}
  for(Face f:faces)if(Color.alpha(f.color)<255)translucent=true;
  int missing=value==null?0:assets!=null?assets.missing(value):value.missingMeshes.size();
  setContentDescription(value==null?"Modelo 3D · aguardando descrição":"Modelo 3D de "+value.name+". Pose URDF, sem TF. "+(missing==0?(value.missingMeshes.isEmpty()?"Primitivas recebidas.":"Malhas locais disponíveis."):missing+" malha(s) não recebida(s).")+(gpu!=null?" OpenGL ES.":" Renderização de compatibilidade.")+" Arraste para girar, pinça para zoom.");invalidate();updateAnimation();
 }

 private void setFullModel(RobotDescription value,RobotAssetBundle assets){
  if(gpu==null)throw new IllegalArgumentException("CAD completo requer OpenGL ES 2");List<RobotCad.Part> parts=new ArrayList<>();faces.clear();model=value;zoom=1;gpuFailure="";
  for(RobotDescription.Visual v:value.visuals){if(v.kind.equals("mesh")){RobotCad.Mesh m=assets.fullMesh(v.mesh);if(m!=null)parts.add(new RobotCad.Part(v,m,RobotCad.scaled(v.transform,v.size),RobotCad.scaled(v.localTransform,v.size)));}else{
    gpuBuilder=new RobotGpuMesh.Builder(RobotGpuMesh.MAX_TRIANGLES);switch(v.kind){case "box":box(v);break;case "sphere":sphere(v);break;case "cylinder":cylinder(v);break;default:break;}RobotGpuMesh.Data primitive=gpuBuilder.finish();gpuBuilder=null;float[] f=new float[primitive.vertices.limit()];primitive.vertices.duplicate().get(f);short[] ix=new short[primitive.triangleCount*3];for(int i=0;i<ix.length;i++)ix[i]=(short)i;double[] low={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};for(int i=0;i<f.length;i+=10)for(int k=0;k<3;k++){low[k]=Math.min(low[k],f[i+k]);hi[k]=Math.max(hi[k],f[i+k]);}RobotCad.Mesh m=new RobotCad.Mesh(Collections.singletonList(new RobotCad.Chunk(f,f.length,ix,ix.length,true)),primitive.triangleCount,low,hi);parts.add(new RobotCad.Part(v,m,RobotDescription.identity(),RobotCad.inverseRigid(value.linkTransforms.get(v.link))));
   }}
  gpuData=RobotGpuMesh.full(new RobotCad.Scene(parts));gpu.setVisibility(gpuData.triangleCount>0?VISIBLE:GONE);setContentDescription("CAD completo de "+value.name+" · "+gpuData.triangleCount+" faces · pose URDF de referência. Arraste para girar, pinça para zoom.");redraw();updateAnimation();
 }
 void resetView(){yaw=.65;pitch=.4;zoom=1;redraw();}
 private void triangle(RobotDescription.Visual v,double[] a,double[] b,double[] c){triangle(v,a,b,c,v.color);}
 private void triangle(RobotDescription.Visual v,double[] a,double[] b,double[] c,int color){if(gpuBuilder!=null){gpuBuilder.add(v,a,b,c,color);return;}if(faces.size()>=MAX_FACES){faces.clear();throw new IllegalArgumentException("Modelo excede 8192 faces visuais");}faces.add(new Face(new double[][]{RobotDescription.point(v.transform,a[0],a[1],a[2]),RobotDescription.point(v.transform,b[0],b[1],b[2]),RobotDescription.point(v.transform,c[0],c[1],c[2])},color));}
 private void mesh(RobotDescription.Visual v,List<RobotGlb.Triangle> triangles){if(triangles==null)return;for(RobotGlb.Triangle t:triangles){double[][] p=new double[3][3];for(int i=0;i<3;i++)for(int j=0;j<3;j++)p[i][j]=t.xyz[i*3+j]*v.size[j];int color=t.embeddedMaterial?t.color:v.materialOverride?v.color:t.color;if(gpuBuilder!=null)gpuBuilder.add(v,p[0],p[1],p[2],color,t.normals);else triangle(v,p[0],p[1],p[2],color);}}
 private void box(RobotDescription.Visual v){double x=v.size[0]/2,y=v.size[1]/2,z=v.size[2]/2;double[][] p={{-x,-y,-z},{x,-y,-z},{x,y,-z},{-x,y,-z},{-x,-y,z},{x,-y,z},{x,y,z},{-x,y,z}};int[][] f={{0,2,1},{0,3,2},{4,5,6},{4,6,7},{0,1,5},{0,5,4},{3,7,6},{3,6,2},{0,4,7},{0,7,3},{1,2,6},{1,6,5}};for(int[] t:f)triangle(v,p[t[0]],p[t[1]],p[t[2]]);}
 private void cylinder(RobotDescription.Visual v){double r=v.size[0],z=v.size[1]/2;for(int i=0;i<12;i++){double a=i*Math.PI/6,b=(i+1)*Math.PI/6;double[] p={r*Math.cos(a),r*Math.sin(a),-z},q={r*Math.cos(b),r*Math.sin(b),-z},u={p[0],p[1],z},w={q[0],q[1],z};triangle(v,p,q,w);triangle(v,p,w,u);triangle(v,new double[]{0,0,-z},q,p);triangle(v,new double[]{0,0,z},u,w);}}
 private static double[] spherical(double r,double lat,double lon){return new double[]{r*Math.cos(lat)*Math.cos(lon),r*Math.cos(lat)*Math.sin(lon),r*Math.sin(lat)};}
 private void sphere(RobotDescription.Visual v){for(int j=0;j<6;j++)for(int i=0;i<12;i++){double a=-Math.PI/2+j*Math.PI/6,b=a+Math.PI/6,c=i*Math.PI/6,d=c+Math.PI/6;double[] p=spherical(v.size[0],a,c),q=spherical(v.size[0],a,d),r=spherical(v.size[0],b,c),s=spherical(v.size[0],b,d);if(j>0)triangle(v,p,q,s);if(j<5)triangle(v,p,s,r);}}
 private double[] rotate(double x,double y,double z){double a=Math.cos(yaw)*x-Math.sin(yaw)*y,b=Math.sin(yaw)*x+Math.cos(yaw)*y;return new double[]{a,Math.cos(pitch)*z+Math.sin(pitch)*b,Math.cos(pitch)*b-Math.sin(pitch)*z};}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(VisualButton.bg(getContext()));drawnFaces=0;if(gpu!=null&&gpuFailure.isEmpty()&&gpuData!=null&&gpuData.triangleCount>0){drawnFaces=gpuData.triangleCount;return;}if(faces.isEmpty()){paint.setColor(VisualButton.muted(getContext()));paint.setTextSize(14*getResources().getDisplayMetrics().scaledDensity);String text=!gpuFailure.isEmpty()?gpuFailure:model==null?"Aguardando descrição do robô":model.visuals.isEmpty()?"Descrição sem elementos visuais":"Malhas não recebidas · sem geometria local";text=TextUtils.ellipsize(text,new android.text.TextPaint(paint),Math.max(1,getWidth()-dp(24)),TextUtils.TruncateAt.END).toString();canvas.drawText(text,(getWidth()-paint.measureText(text))/2,getHeight()/2f,paint);return;}
  double scale=Math.min(getWidth(),getHeight())*.41*zoom/radius;
  for(Face f:faces){double[][] p=new double[3][];f.depth=0;for(int i=0;i<3;i++){double[] w=f.vertices[i];p[i]=rotate(w[0]-cx,w[1]-cy,w[2]-cz);f.xy[i*2]=(float)(getWidth()/2+scale*p[i][0]);f.xy[i*2+1]=(float)(getHeight()/2-scale*p[i][1]);f.z[i]=p[i][2];f.depth+=p[i][2]/3;}double ax=p[1][0]-p[0][0],ay=p[1][1]-p[0][1],az=p[1][2]-p[0][2],bx=p[2][0]-p[0][0],by=p[2][1]-p[0][1],bz=p[2][2]-p[0][2],nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx,n=Math.sqrt(nx*nx+ny*ny+nz*nz);f.light=n<1e-12?.4:.35+.65*Math.max(0,(nx*.3+ny*.6-nz*.74)/n);}
  if(!translucent){drawRaster(canvas);drawnFaces=faces.size();return;}
  faces.sort((a,b)->Double.compare(b.depth,a.depth));paint.setStyle(Paint.Style.FILL);for(Face f:faces){int color=f.color;paint.setColor(Color.argb(Color.alpha(color),(int)(Color.red(color)*f.light),(int)(Color.green(color)*f.light),(int)(Color.blue(color)*f.light)));path.rewind();path.moveTo(f.xy[0],f.xy[1]);path.lineTo(f.xy[2],f.xy[3]);path.lineTo(f.xy[4],f.xy[5]);path.close();canvas.drawPath(path,paint);drawnFaces++;}
 }
 @Override public boolean onTouchEvent(MotionEvent e){pinch.onTouchEvent(e);switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:touching=true;updateAnimation();previousX=e.getX();previousY=e.getY();getParentDisallow(true);return true;case MotionEvent.ACTION_MOVE:if(!pinch.isInProgress()){yaw+=(e.getX()-previousX)*.009;pitch=Math.max(-1.3,Math.min(1.3,pitch+(e.getY()-previousY)*.009));invalidate();}previousX=e.getX();previousY=e.getY();return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:touching=false;updateAnimation();getParentDisallow(false);if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;default:return true;}}
 void releaseRaster(){if(raster!=null)raster.recycle();raster=null;rasterColors=null;rasterDepth=null;if(gpu!=null){driver.scene(null,yaw,pitch,zoom,VisualButton.bg(getContext()));gpu.requestRender();}}
 private void drawRaster(Canvas canvas){
  int w=Math.max(1,Math.min(640,getWidth())),h=Math.max(1,Math.min(640,(int)Math.round(getHeight()*(double)w/Math.max(1,getWidth()))));
  if(raster==null||raster.getWidth()!=w||raster.getHeight()!=h){releaseRaster();raster=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);rasterColors=new int[w*h];rasterDepth=new float[w*h];}
  Arrays.fill(rasterColors,VisualButton.bg(getContext()));Arrays.fill(rasterDepth,Float.POSITIVE_INFINITY);double sx=w/(double)Math.max(1,getWidth()),sy=h/(double)Math.max(1,getHeight());
  for(Face f:faces){double x0=f.xy[0]*sx,y0=f.xy[1]*sy,x1=f.xy[2]*sx,y1=f.xy[3]*sy,x2=f.xy[4]*sx,y2=f.xy[5]*sy,area=(x1-x0)*(y2-y0)-(y1-y0)*(x2-x0);if(Math.abs(area)<1e-8)continue;double inv=1/area,dx0=(y1-y2)*inv,dx1=(y2-y0)*inv;
   int left=Math.max(0,(int)Math.floor(Math.min(x0,Math.min(x1,x2)))),right=Math.min(w-1,(int)Math.ceil(Math.max(x0,Math.max(x1,x2)))),top=Math.max(0,(int)Math.floor(Math.min(y0,Math.min(y1,y2)))),bottom=Math.min(h-1,(int)Math.ceil(Math.max(y0,Math.max(y1,y2))));int color=Color.rgb((int)(Color.red(f.color)*f.light),(int)(Color.green(f.color)*f.light),(int)(Color.blue(f.color)*f.light));
   for(int y=top;y<=bottom;y++){double px=left+.5,py=y+.5,a=((x1-px)*(y2-py)-(y1-py)*(x2-px))*inv,b=((x2-px)*(y0-py)-(y2-py)*(x0-px))*inv;int offset=y*w+left;
    for(int x=left;x<=right;x++,offset++,a+=dx0,b+=dx1){double c=1-a-b;if(a< -1e-9||b< -1e-9||c< -1e-9)continue;double depth=a*f.z[0]+b*f.z[1]+c*f.z[2];if(depth<rasterDepth[offset]){rasterDepth[offset]=(float)depth;rasterColors[offset]=color;}}
   }
  }
  raster.setPixels(rasterColors,0,w,0,0,w,h);paint.setAlpha(255);paint.setFilterBitmap(true);canvas.drawBitmap(raster,null,new Rect(0,0,getWidth(),getHeight()),paint);
 }
 private void getParentDisallow(boolean value){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(value);}
 @Override public boolean performClick(){super.performClick();return true;}
 private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
