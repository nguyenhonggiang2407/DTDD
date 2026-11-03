package vn.yang.slots;
public class Problem extends RuntimeException {
  private final int status;
  public Problem(int status,String message){super(message);this.status=status;}
  public int status(){return status;}
  public static void require(boolean condition,int status,String message){if(!condition)throw new Problem(status,message);}
}
