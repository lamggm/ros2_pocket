package com.lamggm.rospocket;
import android.content.*;import android.content.res.Configuration;import android.view.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class SquareCardTest {
 @Test public void logicalCellIsSquareOnPhoneTabletAndEnlargedFonts(){
  for(float font:new float[]{1f,1.5f})for(int width:new int[]{400,1200}){
   Configuration config=new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());config.fontScale=font;Context c=RuntimeEnvironment.getApplication().createConfigurationContext(config);DashboardBoard b=new DashboardBoard(c);DashboardLayout l=DashboardLayout.defaults();String[] modes={"number","gauge","bar","number"};for(int i=0;i<4;i++)l=l.display("watch"+i,new DashboardLayout.Display(modes[i],"data",0,100,""));b.bind(l,null);for(int i=0;i<4;i++)b.addCard("watch"+i,"Leitura "+i,new MetricCard(c),null);measure(b,width);
   for(int i=0;i<4;i++){View v=b.findViewWithTag("dashboard-card:watch"+i);assertEquals("1x1 must be square at width="+width+" font="+font,v.getWidth(),v.getHeight());assertTrue("Keep square cells compact",v.getWidth()<=Math.round(120*c.getResources().getDisplayMetrics().density*font));assertTrue(v.getRight()<=width);}
   b.edit();b.resize("watch0",2,1);b.save();measure(b,width);View v=b.findViewWithTag("dashboard-card:watch0");assertEquals("2x1 is two square cells plus one gap",2*v.getHeight()+Math.round(12*c.getResources().getDisplayMetrics().density),v.getWidth());
  }
 }
 static void measure(DashboardBoard b,int w){b.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));b.layout(0,0,w,b.getMeasuredHeight());}
}
