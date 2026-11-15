package com.lamggm.rospocket;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.json.*;
import android.view.*;
import java.io.*;
import static org.junit.Assert.*;
/** Native Android card with TEST-ONLY URDF. Never packaged as runtime data. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class RobotCardPreviewTest {
 static String rover(){StringBuilder x=new StringBuilder("<robot name='demo_rover'><material name='body'><color rgba='.17 .65 .56 1'/></material><link name='base'><visual><geometry><box size='1.2 .72 .22'/></geometry><material name='body'/></visual><visual><origin xyz='0 0 .22'/><geometry><cylinder radius='.17' length='.27'/></geometry><material><color rgba='.8 .85 .9 1'/></material></visual><visual><origin xyz='.08 0 .42'/><geometry><sphere radius='.11'/></geometry><material><color rgba='.95 .58 .2 1'/></material></visual></link>");for(int i=0;i<4;i++){double px=i<2?-.38:.38,py=i%2==0?-.4:.4;x.append("<link name='wheel").append(i).append("'><visual><origin rpy='1.57079632679 0 0'/><geometry><cylinder radius='.22' length='.12'/></geometry><material><color rgba='.18 .22 .29 1'/></material></visual></link><joint name='j").append(i).append("' type='fixed'><parent link='base'/><child link='wheel").append(i).append("'/><origin xyz='").append(px).append(" ").append(py).append(" -.1'/></joint>");}return x.append("</robot>").toString();}
 void render(int width,String label)throws Exception{ConnectionActivityTest f=new ConnectionActivityTest();f.setup();try{RobotDashboardTest.Fake d=new RobotDashboardTest.Fake();d.xml=rover();d.snapshot=new JSONObject(d.snapshot).put("topics",RobotDashboardTest.topics("/demo/robot_description")).toString();f.set("bridge",d);f.tick();f.paint();RobotPanel panel=(RobotPanel)f.get("robotPanel");View root=f.a.getWindow().getDecorView().findViewWithTag("app-root");root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));root.layout(0,0,width,1000);int tileHeight=panel.getMeasuredHeight();assertTrue("Public grid supplies a real exact tile height",tileHeight>0);File out=new File("build/reports/robot-v14");assertTrue(out.isDirectory()||out.mkdirs());for(String theme:new String[]{"dark","light"}){UiTheme.save(f.a,theme);f.a.applyAppearance();panel.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(tileHeight,View.MeasureSpec.EXACTLY));panel.layout(0,0,width,panel.getMeasuredHeight());GraphPreviewTest.image(panel,new File(out,label+"-"+theme+"-host-synthetic.png"),width,panel.getHeight());assertTrue(panel.view.drawnFaces>100);}}finally{f.cleanup();}}
 @Test @Config(qualifiers="w412dp-h900dp") public void phoneCardBothThemes()throws Exception{render(380,"phone");}
 @Test @Config(qualifiers="w1280dp-h850dp") public void tabletCardBothThemes()throws Exception{render(880,"tablet");}
}
