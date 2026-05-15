// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
import java.util.StringTokenizer;
import java.util.TreeSet;

public class SinghalMutex extends Process implements Lock {
   LamportClock c = new LamportClock();
   private boolean requesting = false;
   private boolean executing = false;
   private int myts = -1;
   private TreeSet<Integer> requestSet = new TreeSet();
   private TreeSet<Integer> informSet = new TreeSet();

   public SinghalMutex(Linker var1) {
      super(var1);

      for(int var2 = 0; var2 < this.myId; ++var2) {
         this.requestSet.add(var2);
      }

      this.printSets("INIT");
   }

   public synchronized void requestCS() {
      this.requesting = true;
      this.c.tick();
      this.myts = this.c.getValue();
      this.printSets("BEFORE requestCS");
      Integer[] var1 = (Integer[])this.requestSet.toArray(new Integer[0]);

      for(int var2 = 0; var2 < var1.length; ++var2) {
         int var3 = var1[var2];
         this.sendMsg(var3, "singhal_request", this.myts, this.myId);
      }

      this.printSets("AFTER sending REQUESTs");

      while(!this.requestSet.isEmpty()) {
         this.myWait();
      }

      this.requesting = false;
      this.executing = true;
      this.printSets("ENTER CS");
   }

   public synchronized void releaseCS() {
      this.executing = false;
      this.myts = -1;
      this.printSets("BEFORE releaseCS");
      Integer[] var1 = (Integer[])this.informSet.toArray(new Integer[0]);

      for(int var2 = 0; var2 < var1.length; ++var2) {
         int var3 = var1[var2];
         this.informSet.remove(var3);
         this.sendMsg(var3, "singhal_reply", this.c.getValue(), this.myId);
         if (var3 != this.myId) {
            this.requestSet.add(var3);
         }

         this.printSets("releaseCS: sent deferred REPLY to " + var3);
      }

      this.printSets("AFTER releasing CS");
   }

   private boolean myRequestHasPriority(int var1, int var2) {
      if (this.myts == -1) {
         return false;
      } else {
         return this.myts < var1 || this.myts == var1 && this.myId < var2;
      }
   }

   public synchronized void handleMsg(Msg var1, int var2, String var3) {
      StringTokenizer var4 = new StringTokenizer(var1.getMessage());
      if (var3.equals("singhal_request")) {
         int var5 = Integer.parseInt(var4.nextToken());
         int var6 = Integer.parseInt(var4.nextToken());
         this.c.receiveAction(var2, var5);
         if (this.requesting) {
            if (this.myRequestHasPriority(var5, var6)) {
               this.informSet.add(var6);
               this.printSets("REQUEST from " + var6 + " deferred into informSet");
            } else {
               this.sendMsg(var6, "singhal_reply", this.c.getValue(), this.myId);
               if (!this.requestSet.contains(var6)) {
                  this.requestSet.add(var6);
                  this.printSets("REQUEST from " + var6 + ": sent REPLY, added sender to requestSet");
                  this.sendMsg(var6, "singhal_request", this.myts, this.myId);
               } else {
                  this.printSets("REQUEST from " + var6 + ": sent REPLY, sender already in requestSet");
               }
            }
         } else if (this.executing) {
            this.informSet.add(var6);
            this.printSets("REQUEST from " + var6 + " while executing: added to informSet");
         } else {
            if (var6 != this.myId) {
               this.requestSet.add(var6);
            }

            this.sendMsg(var6, "singhal_reply", this.c.getValue(), this.myId);
            this.printSets("REQUEST from " + var6 + " while idle: sent REPLY and added to requestSet");
         }
      } else if (var3.equals("singhal_reply")) {
         int var7 = Integer.parseInt(var4.nextToken());
         this.c.receiveAction(var2, var7);
         this.requestSet.remove(var2);
         this.printSets("REPLY from " + var2 + ": removed from requestSet");
         if (this.requestSet.isEmpty()) {
            this.notifyAll();
         }
      }

   }

   private void printSets(String var1) {
      int var10001 = this.myId;
      System.out.println("P" + var10001 + " [" + var1 + "] requestSet=" + this.requestSet.toString() + " informSet=" + this.informSet.toString());
      System.out.flush();
   }
}
