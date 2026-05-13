public class LockTester {
    public static void main(String[] args) throws Exception {
        Linker comm = null;
        try {
            if (args.length < 4) {
                System.out.println("Usage: java LockTester <baseName> <myId> <numProc> <Singhal|Maekawa>");
                return;
            }

            String baseName = args[0];
            int myId = Integer.parseInt(args[1]);
            int numProc = Integer.parseInt(args[2]);
            String algorithm = args[3];

            comm = new Linker(baseName, myId, numProc);
            Lock lock = null;

            if (algorithm.equals("Singhal"))
                lock = new SinghalMutex(comm);
            else if (algorithm.equals("Maekawa"))
                lock = new MaekawaMutex(comm);
            else {
                System.out.println("Unknown algorithm: " + algorithm);
                System.out.println("Allowed values: Singhal, Maekawa");
                comm.close();
                return;
            }

            for (int i = 0; i < numProc; i++)
                if (i != myId)
                    (new ListenerThread(i, (MsgHandler) lock)).start();

            while (true) {
                System.out.println("Process " + myId + " is not in CS");
                Util.mySleep(2000 + 400 * myId);

                System.out.println("Process " + myId + " requests CS");
                lock.requestCS();

                System.out.println("Process " + myId + " ENTERS CS *****");
                Util.mySleep(1500);
                System.out.println("Process " + myId + " EXITS CS");

                lock.releaseCS();
            }
        } catch (InterruptedException e) {
            if (comm != null) comm.close();
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
            if (comm != null) comm.close();
        }
    }
}
