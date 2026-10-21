import com.lamggm.rospocket.InputRules;
public final class InputRulesTest {
 public static void main(String[] args) {
  for (int d:new int[]{0,1,67,101}) if(InputRules.domain(" "+d+" ")!=d) throw new AssertionError("valid domain rejected");
  for (String s:new String[]{"","-1","102","1.5","NaN","2147483648","67x"}) {
   try {InputRules.domain(s); throw new AssertionError("invalid domain accepted: "+s);}
   catch(IllegalArgumentException expected) {}
  }
  System.out.println("PASS Android domain input validation");
 }
}
